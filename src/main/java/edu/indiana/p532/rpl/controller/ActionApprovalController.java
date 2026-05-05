package edu.indiana.p532.rpl.controller;

import edu.indiana.p532.rpl.domain.operational.plannode.ProposedAction;
import edu.indiana.p532.rpl.dto.ImplementActionRequest;
import edu.indiana.p532.rpl.manager.ActionApprovalManager;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Thin REST controller for the approval workflow.
 * All business logic lives in ActionApprovalManager.
 *
 * POST /api/actions/{id}/submit-for-approval  — PROPOSED → PENDING_APPROVAL
 * POST /api/actions/{id}/approve              — PENDING_APPROVAL → IN_PROGRESS
 * POST /api/actions/{id}/reject               — PENDING_APPROVAL → PROPOSED
 * POST /api/actions/{id}/reopen              — COMPLETED → REOPENED (with reversal entries)
 */
@RestController
@RequestMapping("/api/actions")
public class ActionApprovalController {

    private final ActionApprovalManager approvalManager;

    public ActionApprovalController(ActionApprovalManager approvalManager) {
        this.approvalManager = approvalManager;
    }

    @PostMapping("/{id}/submit-for-approval")
    public Map<String, Object> submitForApproval(@PathVariable Long id) {
        ProposedAction action = approvalManager.submitForApproval(id);
        return toResponse(action);
    }

    @PostMapping("/{id}/approve")
    public Map<String, Object> approve(@PathVariable Long id,
                                       @RequestBody ImplementActionRequest request) {
        ProposedAction action = approvalManager.approve(id, request);
        return toResponse(action);
    }

    @PostMapping("/{id}/reject")
    public Map<String, Object> reject(@PathVariable Long id) {
        ProposedAction action = approvalManager.reject(id);
        return toResponse(action);
    }

    @PostMapping("/{id}/reopen")
    public Map<String, Object> reopen(@PathVariable Long id) {
        ProposedAction action = approvalManager.reopen(id);
        return toResponse(action);
    }

    private Map<String, Object> toResponse(ProposedAction action) {
        return Map.of(
                "id",     action.getId() != null ? action.getId() : 0L,
                "name",   action.getName(),
                "status", action.getStateName()
        );
    }
}
