package edu.indiana.p532.rpl;

import edu.indiana.p532.rpl.domain.AccountKind;
import edu.indiana.p532.rpl.domain.AllocationKind;
import edu.indiana.p532.rpl.domain.ResourceKind;
import edu.indiana.p532.rpl.domain.knowledge.ResourceType;
import edu.indiana.p532.rpl.domain.operational.*;
import edu.indiana.p532.rpl.domain.operational.plannode.ProposedAction;
import edu.indiana.p532.rpl.ledger.ReversalLedgerEntryGenerator;
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
class ReversalLedgerEntryGeneratorTest {

    @Mock private ResourceAllocationRepository allocationRepository;
    @Mock private LedgerTransactionRepository  transactionRepository;
    @Mock private EntryRepository              entryRepository;
    @Mock private AccountRepository            accountRepository;
    @Mock private PostingRuleEngine            postingRuleEngine;
    @Mock private AuditLogEntryRepository      auditLogEntryRepository;

    private ReversalLedgerEntryGenerator generator;
    private ImplementedAction            implementedAction;
    private ProposedAction               proposedAction;

    private static final BigDecimal QTY = new BigDecimal("7");

    @BeforeEach
    void setUp() {
        generator = new ReversalLedgerEntryGenerator();
        injectField(generator, "allocationRepository", allocationRepository);
        injectField(generator, "transactionRepository", transactionRepository);
        injectField(generator, "entryRepository",       entryRepository);
        injectField(generator, "accountRepository",     accountRepository);
        injectField(generator, "postingRuleEngine",     postingRuleEngine);
        injectField(generator, "auditLogEntryRepository", auditLogEntryRepository);

        proposedAction    = new ProposedAction("Reversal Test", null, null, null, null);
        implementedAction = new ImplementedAction(proposedAction, java.time.Instant.now(), "Bob", "Site-B");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ResourceAllocation consumableAlloc() {
        Account pool = new Account("POOL-Steel", AccountKind.POOL, 1L);
        ResourceType rt = new ResourceType("Steel", ResourceKind.CONSUMABLE, "kg");
        rt.setPoolAccount(pool);
        return new ResourceAllocation(1L, rt, QTY, AllocationKind.GENERAL, null, null);
    }

    private void stubHappyPath(ResourceAllocation alloc) {
        LedgerTransaction tx = new LedgerTransaction("reversal tx", 1L);
        when(allocationRepository.findByActionIdAndActionType(any(), any())).thenReturn(List.of(alloc));
        when(transactionRepository.save(any())).thenReturn(tx);
        // buildWithdrawal calls findOrCreateUsageAccount → accountRepository.findByName
        when(accountRepository.findByName(anyString())).thenReturn(Optional.empty());
        when(accountRepository.save(any())).thenReturn(
                new Account("USAGE-Steel-action-null", AccountKind.USAGE, 1L));
        when(entryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void generateEntries_singleConsumableAlloc_withdrawalAmountIsNegatedQuantity() {
        // Arrange
        ResourceAllocation alloc = consumableAlloc();
        stubHappyPath(alloc);

        // Act
        generator.generateEntries(implementedAction);

        // Assert: first saved entry is withdrawal from usage account (negated)
        ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
        verify(entryRepository, times(2)).save(captor.capture());
        Entry withdrawal = captor.getAllValues().get(0);
        assertEquals(QTY.negate(), withdrawal.getAmount(),
                "Reversal withdrawal must negate the original quantity");
    }

    @Test
    void generateEntries_singleConsumableAlloc_depositAmountRestoresToPool() {
        // Arrange
        ResourceAllocation alloc = consumableAlloc();
        stubHappyPath(alloc);

        // Act
        generator.generateEntries(implementedAction);

        // Assert: second saved entry is deposit to pool account (positive)
        ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
        verify(entryRepository, times(2)).save(captor.capture());
        Entry deposit = captor.getAllValues().get(1);
        assertEquals(QTY, deposit.getAmount(),
                "Reversal deposit must restore the original quantity to pool");
    }

    @Test
    void generateEntries_singleConsumableAlloc_doubleEntryBalancesZero() {
        // Arrange
        ResourceAllocation alloc = consumableAlloc();
        stubHappyPath(alloc);

        // Act
        generator.generateEntries(implementedAction);

        // Assert: withdrawal + deposit = 0 (double-entry conservation)
        ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
        verify(entryRepository, times(2)).save(captor.capture());
        BigDecimal sum = captor.getAllValues().get(0).getAmount()
                .add(captor.getAllValues().get(1).getAmount());
        assertEquals(0, sum.compareTo(BigDecimal.ZERO),
                "Double-entry reversal entries must sum to zero");
    }

    @Test
    void generateEntries_noAllocations_returnsNull() {
        // Arrange
        when(allocationRepository.findByActionIdAndActionType(any(), any())).thenReturn(List.of());

        // Act
        LedgerTransaction result = generator.generateEntries(implementedAction);

        // Assert: empty selection → null, no entries persisted
        assertNull(result, "Empty allocation list must produce null (no transaction)");
        verify(entryRepository, never()).save(any());
    }

    @Test
    void generateEntries_afterPost_writesReversalPostedAudit() {
        // Arrange
        ResourceAllocation alloc = consumableAlloc();
        stubHappyPath(alloc);

        // Act
        generator.generateEntries(implementedAction);

        // Assert: afterPost writes exactly one REVERSAL_POSTED audit entry
        ArgumentCaptor<AuditLogEntry> auditCaptor = ArgumentCaptor.forClass(AuditLogEntry.class);
        verify(auditLogEntryRepository, atLeast(1)).save(auditCaptor.capture());
        boolean hasReversal = auditCaptor.getAllValues().stream()
                .anyMatch(a -> "REVERSAL_POSTED".equals(a.getEvent()));
        assertTrue(hasReversal, "Expected REVERSAL_POSTED audit entry from afterPost()");
    }

    @Test
    void appliesTo_anyAction_returnsFalse() {
        // Arrange — appliesTo() always false; called explicitly, not by LedgerEngine
        ReversalLedgerEntryGenerator gen = new ReversalLedgerEntryGenerator();

        // Act & Assert
        assertFalse(gen.appliesTo(implementedAction),
                "appliesTo must return false so LedgerEngine skips this generator");
    }

    @Test
    void validate_anyAllocations_doesNotThrow() {
        // Arrange — validate is intentionally a no-op (already validated at completion)
        ResourceAllocation alloc = consumableAlloc();
        stubHappyPath(alloc);

        // Act & Assert: no exception thrown
        assertDoesNotThrow(() -> generator.generateEntries(implementedAction));
    }

    // ── Reflection helper (mirrors pattern from ConsumableLedgerEntryGeneratorTest) ──

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
