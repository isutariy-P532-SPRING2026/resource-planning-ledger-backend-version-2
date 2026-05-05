package edu.indiana.p532.rpl.domain.operational.state;

import edu.indiana.p532.rpl.exception.IllegalStateTransitionException;

import java.util.List;

/**
 * State pattern interface. Each concrete state is a stateless Spring singleton bean.
 * All mutable data lives in ActionContext (wrapping the JPA entity).
 *
 * legalTransitions() is declared here so ActionController never needs to change
 * when a new state is added — each state knows its own outgoing edges.
 *
 * Week 2: submitForApproval/approve/reject/reopen added as default-throw methods
 * so existing states need zero changes.
 */
public interface ActionState {
    void implement(ActionContext ctx);
    void suspend(ActionContext ctx, String reason);
    void resume(ActionContext ctx);
    void complete(ActionContext ctx);
    void abandon(ActionContext ctx);
    String name();

    /** Returns the event names that are legal to call from this state. */
    List<String> legalTransitions();

    // --- Week 2 transitions — default: throw; overridden only by states that allow them ---

    default void submitForApproval(ActionContext ctx) {
        throw new IllegalStateTransitionException(name(), "submitForApproval");
    }

    default void approve(ActionContext ctx) {
        throw new IllegalStateTransitionException(name(), "approve");
    }

    default void reject(ActionContext ctx) {
        throw new IllegalStateTransitionException(name(), "reject");
    }

    default void reopen(ActionContext ctx) {
        throw new IllegalStateTransitionException(name(), "reopen");
    }
}
