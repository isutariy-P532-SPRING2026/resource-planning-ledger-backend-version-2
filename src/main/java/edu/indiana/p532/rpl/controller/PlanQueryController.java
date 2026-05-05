package edu.indiana.p532.rpl.controller;

import edu.indiana.p532.rpl.domain.ActionStatus;
import edu.indiana.p532.rpl.domain.operational.plannode.ProposedAction;
import edu.indiana.p532.rpl.manager.PlanReportManager;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exposes filtered plan queries backed by FilteredPlanIterator (Change 3 / Week 2).
 * GET /api/plans/{id}/actions           — all leaf actions in the tree.
 * GET /api/plans/{id}/actions?status=X  — leaf actions matching a specific ActionStatus.
 */
@RestController
@RequestMapping("/api/plans")
public class PlanQueryController {

    private final PlanReportManager planReportManager;

    public PlanQueryController(PlanReportManager planReportManager) {
        this.planReportManager = planReportManager;
    }

    @GetMapping("/{id}/actions")
    public List<Map<String, Object>> getActions(
            @PathVariable Long id,
            @RequestParam(required = false) String status) {

        List<ProposedAction> actions = status != null
                ? planReportManager.getActionsByStatus(id, ActionStatus.valueOf(status.toUpperCase()))
                : planReportManager.getAllLeafActions(id);

        return actions.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",        a.getId());
            m.put("name",      a.getName());
            m.put("status",    a.getStatus().name());
            m.put("dependsOn", a.getDependsOn() != null ? a.getDependsOn() : "");
            return m;
        }).toList();
    }
}
