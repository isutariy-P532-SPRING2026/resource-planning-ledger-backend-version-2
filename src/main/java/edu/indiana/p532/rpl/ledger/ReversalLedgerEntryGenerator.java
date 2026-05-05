package edu.indiana.p532.rpl.ledger;

import edu.indiana.p532.rpl.domain.AccountKind;
import edu.indiana.p532.rpl.domain.operational.Account;
import edu.indiana.p532.rpl.domain.operational.AuditLogEntry;
import edu.indiana.p532.rpl.domain.operational.Entry;
import edu.indiana.p532.rpl.domain.operational.ImplementedAction;
import edu.indiana.p532.rpl.domain.operational.LedgerTransaction;
import edu.indiana.p532.rpl.domain.operational.ResourceAllocation;
import edu.indiana.p532.rpl.repository.ResourceAllocationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Template Method subclass that reverses all ledger entries for a completed action.
 * Negates the direction of each allocation: usage account loses quantity back to pool.
 *
 * appliesTo() returns false — called explicitly by ActionApprovalManager.reopen(),
 * not auto-discovered by LedgerEngine, to avoid double-reversals.
 *
 * buildWithdrawal(): takes quantity away from the usage account (negated amount).
 * buildDeposit():    restores quantity to the pool account (positive amount).
 */
@Component
public class ReversalLedgerEntryGenerator extends AbstractLedgerEntryGenerator {

    @Autowired
    private ResourceAllocationRepository allocationRepository;

    @Override
    public boolean appliesTo(ImplementedAction action) {
        return false;
    }

    @Override
    protected List<ResourceAllocation> selectAllocations(ImplementedAction action) {
        return allocationRepository.findByActionIdAndActionType(
                action.getProposedAction().getId(), "PROPOSED_ACTION");
    }

    @Override
    protected void validate(List<ResourceAllocation> allocs) {
        // original entries already validated at completion time
    }

    /**
     * Inlines findOrCreateUsageAccount logic (base method is private).
     */
    @Override
    protected Entry buildWithdrawal(LedgerTransaction tx, ResourceAllocation a) {
        String accountName = "USAGE-" + a.getResourceType().getName() + "-action-" + tx.getOriginatingActionId();
        Account usageAccount = accountRepository.findByName(accountName)
                .orElseGet(() -> accountRepository.save(
                        new Account(accountName, AccountKind.USAGE, a.getResourceType().getId())));
        Instant now = Instant.now();
        return new Entry(tx, usageAccount,
                a.getQuantity().negate(), now, now,
                "Reversal: remove " + a.getQuantity().toPlainString()
                        + " from usage account for action " + tx.getOriginatingActionId());
    }

    @Override
    protected Entry buildDeposit(LedgerTransaction tx, ResourceAllocation a) {
        Account poolAccount = a.getResourceType().getPoolAccount();
        Instant now = Instant.now();
        return new Entry(tx, poolAccount,
                a.getQuantity(), now, now,
                "Reversal: restore " + a.getQuantity().toPlainString()
                        + " to pool account for action " + tx.getOriginatingActionId());
    }

    @Override
    protected void afterPost(LedgerTransaction tx) {
        auditLogEntryRepository.save(new AuditLogEntry(
                "REVERSAL_POSTED", null, null, tx.getOriginatingActionId(),
                "Reversed via ReversalLedgerEntryGenerator for action " + tx.getOriginatingActionId()));
    }
}
