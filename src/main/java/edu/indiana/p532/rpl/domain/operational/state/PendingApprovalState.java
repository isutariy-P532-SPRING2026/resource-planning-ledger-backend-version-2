package edu.indiana.p532.rpl.domain.operational.state;

import edu.indiana.p532.rpl.domain.ActionStatus;
import edu.indiana.p532.rpl.exception.IllegalStateTransitionException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Week 2 state: action has been submitted for approval but not yet approved.
 * approve() → IN_PROGRESS (creates ImplementedAction).
 * reject()  → PROPOSED    (returns to requester for revision).
 */
@Component
public class PendingApprovalState implements ActionState {

    @Override
    public void implement(ActionContext ctx) {
        throw new IllegalStateTransitionException(name(), "implement");
    }

    @Override
    public void suspend(ActionContext ctx, String reason) {
        throw new IllegalStateTransitionException(name(), "suspend");
    }

    @Override
    public void resume(ActionContext ctx) {
        throw new IllegalStateTransitionException(name(), "resume");
    }

    @Override
    public void complete(ActionContext ctx) {
        throw new IllegalStateTransitionException(name(), "complete");
    }

    @Override
    public void abandon(ActionContext ctx) {
        ctx.transitionTo(ActionStatus.ABANDONED);
        ctx.recordAbandon();
    }

    @Override
    public void approve(ActionContext ctx) {
        ctx.transitionTo(ActionStatus.IN_PROGRESS);
        ctx.createImplementedAction();
    }

    @Override
    public void reject(ActionContext ctx) {
        ctx.transitionTo(ActionStatus.PROPOSED);
    }

    @Override
    public String name() { return ActionStatus.PENDING_APPROVAL.name(); }

    @Override
    public List<String> legalTransitions() { return List.of("approve", "reject", "abandon"); }
}
