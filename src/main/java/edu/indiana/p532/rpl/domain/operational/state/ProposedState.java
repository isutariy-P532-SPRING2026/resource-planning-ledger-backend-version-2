package edu.indiana.p532.rpl.domain.operational.state;

import edu.indiana.p532.rpl.domain.ActionStatus;
import edu.indiana.p532.rpl.exception.IllegalStateTransitionException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProposedState implements ActionState {

    @Override
    public void implement(ActionContext ctx) {
        throw new IllegalStateTransitionException(name(), "implement");
    }

    @Override
    public void submitForApproval(ActionContext ctx) {
        ctx.transitionTo(ActionStatus.PENDING_APPROVAL);
        // ImplementedAction is NOT created here — that happens only on approve().
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
        throw new IllegalStateTransitionException(name(), "complete");
    }

    @Override
    public void abandon(ActionContext ctx) {
        ctx.transitionTo(ActionStatus.ABANDONED);
        ctx.recordAbandon();
    }

    @Override
    public String name() { return ActionStatus.PROPOSED.name(); }

    @Override
    public List<String> legalTransitions() {
        return List.of("submitForApproval", "suspend", "abandon");
    }
}
