package edu.indiana.p532.rpl.ledger;

import edu.indiana.p532.rpl.domain.AccountKind;
import edu.indiana.p532.rpl.domain.AllocationKind;
import edu.indiana.p532.rpl.domain.ResourceKind;
import edu.indiana.p532.rpl.domain.operational.Account;
import edu.indiana.p532.rpl.domain.operational.AuditLogEntry;
import edu.indiana.p532.rpl.domain.operational.Entry;
import edu.indiana.p532.rpl.domain.operational.ImplementedAction;
import edu.indiana.p532.rpl.domain.operational.LedgerTransaction;
import edu.indiana.p532.rpl.domain.operational.ResourceAllocation;
import edu.indiana.p532.rpl.repository.ResourceAllocationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Template Method subclass for ASSET resource allocations (Week 2 / Change 2).
 *
 * Rules:
 * - Only handles SPECIFIC allocations (AllocationKind.SPECIFIC) of ASSET resource types.
 * - Requires a non-null, non-blank timePeriod in ISO-8601 interval format "start/end".
 * - Ledger amount = duration in hours computed from timePeriod (not the quantity field).
 * - afterPost() appends one ASSET_UTILISATION audit record per allocation.
 *
 * appliesTo() returns false — called explicitly by ActionManager.onComplete(),
 * not auto-discovered by LedgerEngine (which handles consumables).
 */
@Component
public class AssetLedgerEntryGenerator extends AbstractLedgerEntryGenerator {

    @Autowired
    private ResourceAllocationRepository allocationRepository;

    @Override
    public boolean appliesTo(ImplementedAction action) {
        return false; // called explicitly by ActionManager.onComplete()
    }

    @Override
    protected List<ResourceAllocation> selectAllocations(ImplementedAction action) {
        return allocationRepository
                .findByActionIdAndActionType(action.getProposedAction().getId(), "PROPOSED_ACTION")
                .stream()
                .filter(a -> a.getResourceType().getKind() == ResourceKind.ASSET
                          && a.getKind() == AllocationKind.SPECIFIC)
                .toList();
    }

    @Override
    protected void validate(List<ResourceAllocation> allocs) {
        for (ResourceAllocation a : allocs) {
            if (a.getAssetId() == null || a.getAssetId().isBlank()) {
                throw new IllegalArgumentException(
                        "SPECIFIC asset allocation requires a non-blank assetId");
            }
            if (a.getTimePeriod() == null || a.getTimePeriod().isBlank()) {
                throw new IllegalArgumentException(
                        "SPECIFIC asset allocation requires a timePeriod for duration calculation"
                        + " (asset=" + a.getAssetId() + ")");
            }
            if (!a.getTimePeriod().contains("/")) {
                throw new IllegalArgumentException(
                        "timePeriod must be ISO-8601 interval 'start/end', got: " + a.getTimePeriod());
            }
            BigDecimal hours = durationHours(a.getTimePeriod());
            if (hours.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(
                        "timePeriod must have positive duration, got: " + a.getTimePeriod());
            }
        }
    }

    @Override
    protected Entry buildWithdrawal(LedgerTransaction tx, ResourceAllocation a) {
        BigDecimal hours = durationHours(a.getTimePeriod());
        Instant now = Instant.now();
        return new Entry(tx, a.getResourceType().getPoolAccount(),
                hours.negate(), now, now,
                "Asset utilisation withdrawal: asset=" + a.getAssetId()
                + " period=" + a.getTimePeriod()
                + " hours=" + hours.toPlainString()
                + " action=" + tx.getOriginatingActionId());
    }

    /**
     * Inlines findOrCreateUsageAccount logic (base method is private).
     */
    @Override
    protected Entry buildDeposit(LedgerTransaction tx, ResourceAllocation a) {
        BigDecimal hours = durationHours(a.getTimePeriod());
        String accountName = "USAGE-" + a.getResourceType().getName() + "-action-" + tx.getOriginatingActionId();
        Account usageAccount = accountRepository.findByName(accountName)
                .orElseGet(() -> accountRepository.save(
                        new Account(accountName, AccountKind.USAGE, a.getResourceType().getId())));
        Instant now = Instant.now();
        return new Entry(tx, usageAccount,
                hours, now, now,
                "Asset utilisation deposit: asset=" + a.getAssetId()
                + " period=" + a.getTimePeriod()
                + " hours=" + hours.toPlainString()
                + " action=" + tx.getOriginatingActionId());
    }

    @Override
    protected void afterPost(LedgerTransaction tx) {
        allocationRepository
                .findByActionIdAndActionType(tx.getOriginatingActionId(), "PROPOSED_ACTION")
                .stream()
                .filter(a -> a.getResourceType().getKind() == ResourceKind.ASSET
                          && a.getKind() == AllocationKind.SPECIFIC)
                .forEach(a -> {
                    BigDecimal hours = durationHours(a.getTimePeriod());
                    String details = "UTILISATION: asset=" + a.getAssetId()
                            + " type=" + a.getResourceType().getName()
                            + " period=" + a.getTimePeriod()
                            + " hours=" + hours.toPlainString()
                            + " action=" + tx.getOriginatingActionId();
                    AuditLogEntry util = new AuditLogEntry(
                            "ASSET_UTILISATION", null, null, tx.getOriginatingActionId(), details);
                    util.setTransactionId(tx.getId());
                    auditLogEntryRepository.save(util);
                });
    }

    private BigDecimal durationHours(String timePeriod) {
        String[] parts = timePeriod.split("/", 2);
        try {
            Instant start = Instant.parse(parts[0]);
            Instant end   = Instant.parse(parts[1]);
            return BigDecimal.valueOf(ChronoUnit.HOURS.between(start, end));
        } catch (DateTimeException e) {
            LocalDate start = LocalDate.parse(parts[0]);
            LocalDate end   = LocalDate.parse(parts[1]);
            return BigDecimal.valueOf(ChronoUnit.DAYS.between(start, end) * 24L);
        }
    }
}
