package edu.indiana.p532.rpl.domain.operational.state;

import edu.indiana.p532.rpl.domain.ActionStatus;
import edu.indiana.p532.rpl.exception.IllegalStateTransitionException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Week 2 state: action was COMPLETED, then reopened (reversal entries created).
 * Behaves like IN_PROGRESS — can re-complete (generates new forward ledger entries),
 * suspend, or abandon.
 */
@Component
public class ReopenedState implements ActionState {

    @Override
    public void implement(ActionContext ctx) {
        throw new IllegalStateTransitionException(name(), "implement");
    }

    @Override
    public void suspend(ActionContext ctx, String reason) {
        ctx.transitionTo(ActionStatus.SUSPENDED);
        ctx.recordSuspension();
    }

    @Override
    public void resume(ActionContext ctx) {
        throw new IllegalStateTransitionException(name(), "resume");
    }

    @Override
    public void complete(ActionContext ctx) {
        ctx.transitionTo(ActionStatus.COMPLETED);
        ctx.generateLedgerEntries();
    }

    @Override
    public void abandon(ActionContext ctx) {
        ctx.transitionTo(ActionStatus.ABANDONED);
        ctx.recordAbandon();
    }

    @Override
    public String name() { return ActionStatus.REOPENED.name(); }

    @Override
    public List<String> legalTransitions() { return List.of("complete", "suspend", "abandon"); }
}
