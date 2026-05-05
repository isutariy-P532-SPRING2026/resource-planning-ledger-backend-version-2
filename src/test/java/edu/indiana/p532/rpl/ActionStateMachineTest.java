package edu.indiana.p532.rpl;

import edu.indiana.p532.rpl.domain.ActionStatus;
import edu.indiana.p532.rpl.domain.operational.plannode.ProposedAction;
import edu.indiana.p532.rpl.domain.operational.state.*;
import edu.indiana.p532.rpl.exception.IllegalStateTransitionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActionStateMachineTest {

    @Mock
    private ActionContextCallback callback;

    private ProposedAction action;
    private ActionContext ctx;

    @BeforeEach
    void setUp() {
        action = new ProposedAction("Test Action", null, "Alice", null, "Lab A");
        ctx = new ActionContext(action, callback)
                .withImplementData("Alice", "Lab A", Instant.now());
    }

    // --- Week 2: PROPOSED → PENDING_APPROVAL (implement now queues for approval) ---

    @Test
    void implement_proposedState_throwsIllegalStateTransitionException() {
        ProposedState state = new ProposedState();
        assertThrows(IllegalStateTransitionException.class, () -> state.implement(ctx));
    }

    @Test
    void submitForApproval_proposedState_transitionsToPendingApproval() {
        ProposedState state = new ProposedState();
        state.submitForApproval(ctx);
        assertEquals(ActionStatus.PENDING_APPROVAL.name(), action.getStateName());
        verify(callback, never()).onImplement(any(), any(), any(), any());
    }

    // --- Week 2: PENDING_APPROVAL → approve/reject ---

    @Test
    void approve_pendingApprovalState_transitionsToInProgressAndCreatesImplementedAction() {
        action.setStateName(ActionStatus.PENDING_APPROVAL.name());
        PendingApprovalState state = new PendingApprovalState();
        state.approve(ctx);
        assertEquals(ActionStatus.IN_PROGRESS.name(), action.getStateName());
        verify(callback).onImplement(eq(action), any(), any(), any());
    }

    @Test
    void reject_pendingApprovalState_transitionsBackToProposed() {
        action.setStateName(ActionStatus.PENDING_APPROVAL.name());
        PendingApprovalState state = new PendingApprovalState();
        state.reject(ctx);
        assertEquals(ActionStatus.PROPOSED.name(), action.getStateName());
    }

    // --- Week 2: COMPLETED → REOPENED ---

    @Test
    void reopen_completedState_transitionsToReopened() {
        action.setStateName(ActionStatus.COMPLETED.name());
        CompletedState state = new CompletedState();
        state.reopen(ctx);
        assertEquals(ActionStatus.REOPENED.name(), action.getStateName());
    }

    // --- Week 2: REOPENED → complete/suspend/abandon ---

    @Test
    void complete_reopenedState_transitionsToCompletedAndFiresLedger() {
        action.setStateName(ActionStatus.REOPENED.name());
        ReopenedState state = new ReopenedState();
        state.complete(ctx);
        assertEquals(ActionStatus.COMPLETED.name(), action.getStateName());
        verify(callback).onComplete(action);
    }

    // --- Existing legal transitions (unchanged) ---

    @Test
    void suspend_proposedState_transitionsToSuspended() {
        ProposedState state = new ProposedState();
        ctx.withSuspensionReason("Awaiting parts");
        state.suspend(ctx, "Awaiting parts");
        assertEquals(ActionStatus.SUSPENDED.name(), action.getStateName());
        verify(callback).onSuspend(action, "Awaiting parts");
    }

    @Test
    void abandon_proposedState_transitionsToAbandoned() {
        ProposedState state = new ProposedState();
        state.abandon(ctx);
        assertEquals(ActionStatus.ABANDONED.name(), action.getStateName());
    }

    @Test
    void resume_suspendedState_transitionsToProposed() {
        action.setStateName(ActionStatus.SUSPENDED.name());
        SuspendedState state = new SuspendedState();
        state.resume(ctx);
        assertEquals(ActionStatus.PROPOSED.name(), action.getStateName());
    }

    @Test
    void abandon_suspendedState_transitionsToAbandoned() {
        action.setStateName(ActionStatus.SUSPENDED.name());
        SuspendedState state = new SuspendedState();
        state.abandon(ctx);
        assertEquals(ActionStatus.ABANDONED.name(), action.getStateName());
    }

    @Test
    void complete_inProgressState_transitionsToCompletedAndFiresLedger() {
        action.setStateName(ActionStatus.IN_PROGRESS.name());
        InProgressState state = new InProgressState();
        state.complete(ctx);
        assertEquals(ActionStatus.COMPLETED.name(), action.getStateName());
        verify(callback).onComplete(action);
    }

    @Test
    void suspend_inProgressState_transitionsToSuspended() {
        action.setStateName(ActionStatus.IN_PROGRESS.name());
        InProgressState state = new InProgressState();
        ctx.withSuspensionReason("Resource unavailable");
        state.suspend(ctx, "Resource unavailable");
        assertEquals(ActionStatus.SUSPENDED.name(), action.getStateName());
        verify(callback).onSuspend(action, "Resource unavailable");
    }

    // --- Illegal transitions ---

    @Test
    void implement_completedState_throwsIllegalStateTransitionException() {
        action.setStateName(ActionStatus.COMPLETED.name());
        CompletedState state = new CompletedState();
        assertThrows(IllegalStateTransitionException.class, () -> state.implement(ctx));
    }

    @Test
    void suspend_completedState_throwsIllegalStateTransitionException() {
        CompletedState state = new CompletedState();
        assertThrows(IllegalStateTransitionException.class, () -> state.suspend(ctx, "reason"));
    }

    @Test
    void abandon_completedState_throwsIllegalStateTransitionException() {
        CompletedState state = new CompletedState();
        assertThrows(IllegalStateTransitionException.class, () -> state.abandon(ctx));
    }

    @Test
    void implement_abandonedState_throwsIllegalStateTransitionException() {
        AbandonedState state = new AbandonedState();
        assertThrows(IllegalStateTransitionException.class, () -> state.implement(ctx));
    }

    @Test
    void resume_inProgressState_throwsIllegalStateTransitionException() {
        action.setStateName(ActionStatus.IN_PROGRESS.name());
        InProgressState state = new InProgressState();
        assertThrows(IllegalStateTransitionException.class, () -> state.resume(ctx));
    }

    @Test
    void suspend_suspendedState_throwsIllegalStateTransitionException() {
        action.setStateName(ActionStatus.SUSPENDED.name());
        SuspendedState state = new SuspendedState();
        assertThrows(IllegalStateTransitionException.class, () -> state.suspend(ctx, "reason"));
    }

    @Test
    void implement_suspendedState_throwsIllegalStateTransitionException() {
        action.setStateName(ActionStatus.SUSPENDED.name());
        SuspendedState state = new SuspendedState();
        assertThrows(IllegalStateTransitionException.class, () -> state.implement(ctx));
    }

    @Test
    void reopen_inProgressState_throwsIllegalStateTransitionException() {
        action.setStateName(ActionStatus.IN_PROGRESS.name());
        InProgressState state = new InProgressState();
        assertThrows(IllegalStateTransitionException.class, () -> state.reopen(ctx));
    }

    @Test
    void approve_proposedState_throwsIllegalStateTransitionException() {
        ProposedState state = new ProposedState();
        assertThrows(IllegalStateTransitionException.class, () -> state.approve(ctx));
    }
}
