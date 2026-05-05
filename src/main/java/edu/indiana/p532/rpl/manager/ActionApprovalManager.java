package edu.indiana.p532.rpl.manager;

import edu.indiana.p532.rpl.domain.ActionStatus;
import edu.indiana.p532.rpl.domain.operational.AuditLogEntry;
import edu.indiana.p532.rpl.domain.operational.ImplementedAction;
import edu.indiana.p532.rpl.domain.operational.Suspension;
import edu.indiana.p532.rpl.domain.operational.plannode.ProposedAction;
import edu.indiana.p532.rpl.domain.operational.state.ActionContext;
import edu.indiana.p532.rpl.domain.operational.state.ActionContextCallback;
import edu.indiana.p532.rpl.dto.ImplementActionRequest;
import edu.indiana.p532.rpl.engine.ActionStateMachineEngine;
import edu.indiana.p532.rpl.exception.ResourceNotFoundException;
import edu.indiana.p532.rpl.ledger.ReversalLedgerEntryGenerator;
import edu.indiana.p532.rpl.repository.AuditLogEntryRepository;
import edu.indiana.p532.rpl.repository.ImplementedActionRepository;
import edu.indiana.p532.rpl.repository.ProposedActionRepository;
import edu.indiana.p532.rpl.repository.SuspensionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Handles the approval workflow: submit-for-approval, approve, reject, reopen.
 * Uses the ActionStateMachineEngine for state transitions and calls
 * ReversalLedgerEntryGenerator explicitly on reopen — keeping reversal logic
 * out of LedgerManager for this path.
 */
@Service
public class ActionApprovalManager {

    private final ProposedActionRepository actionRepository;
    private final ImplementedActionRepository implementedActionRepository;
    private final SuspensionRepository suspensionRepository;
    private final AuditLogEntryRepository auditLogEntryRepository;
    private final ActionStateMachineEngine stateMachineEngine;
    private final ReversalLedgerEntryGenerator reversalLedgerEntryGenerator;

    public ActionApprovalManager(ProposedActionRepository actionRepository,
                                 ImplementedActionRepository implementedActionRepository,
                                 SuspensionRepository suspensionRepository,
                                 AuditLogEntryRepository auditLogEntryRepository,
                                 ActionStateMachineEngine stateMachineEngine,
                                 ReversalLedgerEntryGenerator reversalLedgerEntryGenerator) {
        this.actionRepository = actionRepository;
        this.implementedActionRepository = implementedActionRepository;
        this.suspensionRepository = suspensionRepository;
        this.auditLogEntryRepository = auditLogEntryRepository;
        this.stateMachineEngine = stateMachineEngine;
        this.reversalLedgerEntryGenerator = reversalLedgerEntryGenerator;
    }

    @Transactional
    public ProposedAction submitForApproval(Long actionId) {
        ProposedAction action = load(actionId);
        stateMachineEngine.resolve(action.getStateName())
                .submitForApproval(new ActionContext(action, callback()));
        audit("SUBMIT_FOR_APPROVAL", actionId);
        return actionRepository.save(action);
    }

    @Transactional
    public ProposedAction approve(Long actionId, ImplementActionRequest request) {
        ProposedAction action = load(actionId);
        ActionContext ctx = new ActionContext(action, callback())
                .withImplementData(
                        request.actualParty(),
                        request.actualLocation(),
                        request.actualStart() != null ? request.actualStart() : Instant.now());
        stateMachineEngine.resolve(action.getStateName()).approve(ctx);
        audit("APPROVE", actionId);
        return actionRepository.save(action);
    }

    @Transactional
    public ProposedAction reject(Long actionId) {
        ProposedAction action = load(actionId);
        stateMachineEngine.resolve(action.getStateName())
                .reject(new ActionContext(action, callback()));
        audit("REJECT", actionId);
        return actionRepository.save(action);
    }

    /**
     * Transitions COMPLETED → REOPENED and generates reversal ledger entries
     * via ReversalLedgerEntryGenerator. The state's reopen() fires
     * callback.onReopen() which is a no-op here; the explicit generator call
     * below ensures the double-entry reversal is posted.
     */
    @Transactional
    public ProposedAction reopen(Long actionId) {
        ProposedAction action = load(actionId);
        stateMachineEngine.resolve(action.getStateName())
                .reopen(new ActionContext(action, callback()));
        actionRepository.save(action);

        ImplementedAction impl = implementedActionRepository.findByProposedActionId(actionId)
                .orElseThrow(() -> new IllegalStateException(
                        "No ImplementedAction found for action " + actionId));
        impl.setStatus(ActionStatus.REOPENED);
        implementedActionRepository.save(impl);

        reversalLedgerEntryGenerator.generateEntries(impl);
        audit("REOPEN", actionId);
        return action;
    }

    // --- ActionContextCallback for this approval path ---

    private ActionContextCallback callback() {
        return new ActionContextCallback() {
            @Override
            public void onImplement(ProposedAction action, String party, String location, Instant start) {
                ImplementedAction impl = new ImplementedAction(
                        action, start != null ? start : Instant.now(), party, location);
                implementedActionRepository.save(impl);
            }

            @Override
            public void onSuspend(ProposedAction action, String reason) {
                suspensionRepository.save(new Suspension(action, reason));
            }

            @Override
            public void onComplete(ProposedAction action) {
                // not invoked from approval operations
            }

            @Override
            public void onAbandon(ProposedAction action) {
                auditLogEntryRepository.save(
                        new AuditLogEntry("ABANDON_DETAIL", null, null, action.getId(), null));
            }

            @Override
            public boolean hasImplementation(ProposedAction action) {
                return implementedActionRepository.findByProposedActionId(action.getId()).isPresent();
            }

            // onReopen default is no-op — reopen() handles reversal explicitly above
        };
    }

    private ProposedAction load(Long id) {
        return actionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProposedAction not found: " + id));
    }

    private void audit(String event, Long actionId) {
        auditLogEntryRepository.save(new AuditLogEntry(event, null, null, actionId, null));
    }
}
