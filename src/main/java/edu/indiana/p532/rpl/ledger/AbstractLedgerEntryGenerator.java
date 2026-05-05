package edu.indiana.p532.rpl.ledger;

import edu.indiana.p532.rpl.domain.operational.*;
import edu.indiana.p532.rpl.domain.knowledge.ResourceType;
import edu.indiana.p532.rpl.posting.PostingRuleEngine;
import edu.indiana.p532.rpl.repository.AccountRepository;
import edu.indiana.p532.rpl.repository.AuditLogEntryRepository;
import edu.indiana.p532.rpl.repository.EntryRepository;
import edu.indiana.p532.rpl.repository.LedgerTransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Template Method pattern. The skeleton (generateEntries) is final and cannot be overridden.
 * Subclasses provide selectAllocations() and validate(); they may override buildWithdrawal(),
 * buildDeposit(), and afterPost() hooks.
 *
 * Extension: add Week-2 AssetLedgerEntryGenerator as a new subclass with zero changes here.
 * LedgerEngine injects List<AbstractLedgerEntryGenerator> and calls all that apply.
 */
public abstract class AbstractLedgerEntryGenerator {

    @Autowired
    protected LedgerTransactionRepository transactionRepository;
    @Autowired
    protected EntryRepository entryRepository;
    @Autowired
    protected AccountRepository accountRepository;
    @Autowired
    protected PostingRuleEngine postingRuleEngine;
    @Autowired
    protected AuditLogEntryRepository auditLogEntryRepository;

    /** Called by LedgerEngine to filter which generator handles a given action. */
    public abstract boolean appliesTo(ImplementedAction action);

    /** Template method — final, defines the skeleton. */
    public final LedgerTransaction generateEntries(ImplementedAction action) {
        List<ResourceAllocation> allocs = selectAllocations(action);
        if (allocs.isEmpty()) return null;
        validate(allocs);
        LedgerTransaction tx = createTransaction(action);
        for (ResourceAllocation a : allocs) {
            Entry withdrawal = buildWithdrawal(tx, a);
            Entry deposit = buildDeposit(tx, a);
            postEntries(tx, withdrawal, deposit);
        }
        afterPost(tx);
        return tx;
    }

    protected abstract List<ResourceAllocation> selectAllocations(ImplementedAction action);

    protected abstract void validate(List<ResourceAllocation> allocs);

    protected Entry buildWithdrawal(LedgerTransaction tx, ResourceAllocation a) {
        Account poolAccount = a.getResourceType().getPoolAccount();
        Instant now = Instant.now();
        return new Entry(tx, poolAccount, a.getQuantity().negate(), now, now,
                "Withdrawal for action " + tx.getOriginatingActionId());
    }

    protected Entry buildDeposit(LedgerTransaction tx, ResourceAllocation a) {
        Account usageAccount = findOrCreateUsageAccount(a.getResourceType(), tx.getOriginatingActionId());
        Instant now = Instant.now();
        return new Entry(tx, usageAccount, a.getQuantity(), now, now,
                "Deposit for action " + tx.getOriginatingActionId());
    }

    /** Hook — empty by default. Week 2 AssetLedgerEntryGenerator overrides this. */
    protected void afterPost(LedgerTransaction tx) {}

    protected LedgerTransaction createTransaction(ImplementedAction action) {
        LedgerTransaction tx = new LedgerTransaction(
                "Completion ledger for action " + action.getProposedAction().getId(),
                action.getProposedAction().getId());
        return transactionRepository.save(tx);
    }

    /** Final — ensures double-entry conservation is always applied. */
    private void postEntries(LedgerTransaction tx, Entry withdrawal, Entry deposit) {
        // conservation check: withdrawal + deposit must sum to zero
        BigDecimal sum = withdrawal.getAmount().add(deposit.getAmount());
        if (sum.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Double-entry imbalance: " + sum);
        }
        Entry savedWithdrawal = entryRepository.save(withdrawal);
        Entry savedDeposit = entryRepository.save(deposit);

        // fire posting rules eagerly
        postingRuleEngine.applyRules(savedWithdrawal, withdrawal.getAccount());
        postingRuleEngine.applyRules(savedDeposit, deposit.getAccount());

        // audit the full double-entry transaction as one record
        BigDecimal txSum = savedWithdrawal.getAmount().add(savedDeposit.getAmount());
        String details = "debit: " + withdrawal.getAccount().getName()
                + " " + savedWithdrawal.getAmount().toPlainString()
                + " | credit: " + deposit.getAccount().getName()
                + " +" + savedDeposit.getAmount().toPlainString()
                + " | sum: " + txSum.toPlainString();
        AuditLogEntry txAudit = new AuditLogEntry(
                "TRANSACTION_POSTED",
                withdrawal.getAccount().getId(),
                null,
                tx.getOriginatingActionId(),
                details);
        txAudit.setTransactionId(tx.getId());
        auditLogEntryRepository.save(txAudit);
    }

    private Account findOrCreateUsageAccount(ResourceType resourceType, Long actionId) {
        String accountName = "USAGE-" + resourceType.getName() + "-action-" + actionId;
        return accountRepository.findByName(accountName)
                .orElseGet(() -> accountRepository.save(
                        new Account(accountName,
                                edu.indiana.p532.rpl.domain.AccountKind.USAGE,
                                resourceType.getId())));
    }
}
