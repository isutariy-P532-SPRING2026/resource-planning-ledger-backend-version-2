package edu.indiana.p532.rpl;

import edu.indiana.p532.rpl.domain.AccountKind;
import edu.indiana.p532.rpl.domain.AllocationKind;
import edu.indiana.p532.rpl.domain.ResourceKind;
import edu.indiana.p532.rpl.domain.knowledge.ResourceType;
import edu.indiana.p532.rpl.domain.operational.*;
import edu.indiana.p532.rpl.domain.operational.plannode.ProposedAction;
import edu.indiana.p532.rpl.ledger.AssetLedgerEntryGenerator;
import edu.indiana.p532.rpl.posting.PostingRuleEngine;
import edu.indiana.p532.rpl.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssetLedgerEntryGeneratorTest {

    @Mock private ResourceAllocationRepository allocationRepository;
    @Mock private LedgerTransactionRepository  transactionRepository;
    @Mock private EntryRepository              entryRepository;
    @Mock private AccountRepository            accountRepository;
    @Mock private PostingRuleEngine            postingRuleEngine;
    @Mock private AuditLogEntryRepository      auditLogEntryRepository;

    private AssetLedgerEntryGenerator generator;
    private ImplementedAction          implementedAction;
    private ProposedAction             proposedAction;

    // A 24-hour period (date-only ISO-8601 interval)
    private static final String PERIOD_24H = "2026-01-01/2026-01-02";

    @BeforeEach
    void setUp() {
        generator = new AssetLedgerEntryGenerator();
        injectField(generator, "allocationRepository", allocationRepository);
        injectField(generator, "transactionRepository", transactionRepository);
        injectField(generator, "entryRepository",       entryRepository);
        injectField(generator, "accountRepository",     accountRepository);
        injectField(generator, "postingRuleEngine",     postingRuleEngine);
        injectField(generator, "auditLogEntryRepository", auditLogEntryRepository);

        proposedAction    = new ProposedAction("Asset Test", null, null, null, null);
        implementedAction = new ImplementedAction(proposedAction, java.time.Instant.now(), "Bob", "Site-A");
    }

    private ResourceAllocation specificAssetAlloc(ResourceType rt) {
        return new ResourceAllocation(1L, rt, BigDecimal.ONE, AllocationKind.SPECIFIC, "CRANE-001", PERIOD_24H);
    }

    @Test
    void generateEntries_specificAssetWith24hPeriod_amountIsDurationHours() {
        Account pool = new Account("POOL-Crane", AccountKind.POOL, 10L);
        ResourceType rt = new ResourceType("Crane", ResourceKind.ASSET, "unit");
        rt.setPoolAccount(pool);
        ResourceAllocation alloc = specificAssetAlloc(rt);

        LedgerTransaction tx = new LedgerTransaction("asset tx", 1L);
        when(allocationRepository.findByActionIdAndActionType(any(), any())).thenReturn(List.of(alloc));
        when(transactionRepository.save(any())).thenReturn(tx);
        when(accountRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(accountRepository.save(any()))
                .thenReturn(new Account("USAGE-Crane-action-null", AccountKind.USAGE, 10L));
        when(entryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        LedgerTransaction result = generator.generateEntries(implementedAction);

        assertNotNull(result);
        // capture saved entries and verify amounts are ±24h
        ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
        verify(entryRepository, times(2)).save(captor.capture());
        List<Entry> saved = captor.getAllValues();
        BigDecimal withdrawal = saved.get(0).getAmount();
        BigDecimal deposit    = saved.get(1).getAmount();
        assertEquals(new BigDecimal("-24"), withdrawal);
        assertEquals(new BigDecimal("24"),  deposit);
    }

    @Test
    void generateEntries_specificAssetWith24hPeriod_writesUtilisationAudit() {
        Account pool = new Account("POOL-Crane", AccountKind.POOL, 10L);
        ResourceType rt = new ResourceType("Crane", ResourceKind.ASSET, "unit");
        rt.setPoolAccount(pool);

        LedgerTransaction tx = new LedgerTransaction("asset tx", 1L);
        when(allocationRepository.findByActionIdAndActionType(any(), any()))
                .thenReturn(List.of(specificAssetAlloc(rt)));
        when(transactionRepository.save(any())).thenReturn(tx);
        when(accountRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(accountRepository.save(any()))
                .thenReturn(new Account("USAGE-Crane-action-null", AccountKind.USAGE, 10L));
        when(entryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        generator.generateEntries(implementedAction);

        // One TRANSACTION_POSTED audit (from abstract base) + one ASSET_UTILISATION (afterPost)
        ArgumentCaptor<AuditLogEntry> auditCaptor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogEntryRepository, atLeast(2)).save(auditCaptor.capture());
        boolean hasUtilisation = auditCaptor.getAllValues().stream()
                .anyMatch(a -> "ASSET_UTILISATION".equals(a.getEvent()));
        assertTrue(hasUtilisation, "Expected ASSET_UTILISATION audit entry");
    }

    @Test
    void generateEntries_generalAssetAllocation_returnsNull() {
        Account pool = new Account("POOL-Loader", AccountKind.POOL, 11L);
        ResourceType rt = new ResourceType("Loader", ResourceKind.ASSET, "unit");
        rt.setPoolAccount(pool);
        // GENERAL — not SPECIFIC, should be filtered out
        ResourceAllocation general = new ResourceAllocation(1L, rt, BigDecimal.ONE,
                AllocationKind.GENERAL, null, PERIOD_24H);

        when(allocationRepository.findByActionIdAndActionType(any(), any())).thenReturn(List.of(general));

        LedgerTransaction result = generator.generateEntries(implementedAction);

        assertNull(result);
        verify(entryRepository, never()).save(any());
    }

    @Test
    void generateEntries_consumableAllocationOnly_returnsNull() {
        Account pool = new Account("POOL-Steel", AccountKind.POOL, 12L);
        ResourceType rt = new ResourceType("Steel", ResourceKind.CONSUMABLE, "kg");
        rt.setPoolAccount(pool);
        ResourceAllocation cons = new ResourceAllocation(1L, rt, new BigDecimal("5"),
                AllocationKind.SPECIFIC, "BATCH-01", PERIOD_24H);

        when(allocationRepository.findByActionIdAndActionType(any(), any())).thenReturn(List.of(cons));

        assertNull(generator.generateEntries(implementedAction));
        verify(entryRepository, never()).save(any());
    }

    @Test
    void validate_missingTimePeriod_throwsIllegalArgumentException() {
        Account pool = new Account("POOL-Truck", AccountKind.POOL, 13L);
        ResourceType rt = new ResourceType("Truck", ResourceKind.ASSET, "unit");
        rt.setPoolAccount(pool);
        ResourceAllocation noperiod = new ResourceAllocation(1L, rt, BigDecimal.ONE,
                AllocationKind.SPECIFIC, "TRUCK-001", null);

        when(allocationRepository.findByActionIdAndActionType(any(), any())).thenReturn(List.of(noperiod));

        assertThrows(IllegalArgumentException.class,
                () -> generator.generateEntries(implementedAction));
    }

    @Test
    void validate_missingAssetId_throwsIllegalArgumentException() {
        Account pool = new Account("POOL-Truck", AccountKind.POOL, 14L);
        ResourceType rt = new ResourceType("Truck", ResourceKind.ASSET, "unit");
        rt.setPoolAccount(pool);
        ResourceAllocation noid = new ResourceAllocation(1L, rt, BigDecimal.ONE,
                AllocationKind.SPECIFIC, null, PERIOD_24H);

        when(allocationRepository.findByActionIdAndActionType(any(), any())).thenReturn(List.of(noid));

        assertThrows(IllegalArgumentException.class,
                () -> generator.generateEntries(implementedAction));
    }

    private void injectField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field;
            try {
                field = target.getClass().getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                field = target.getClass().getSuperclass().getDeclaredField(fieldName);
            }
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject field: " + fieldName, e);
        }
    }
}
