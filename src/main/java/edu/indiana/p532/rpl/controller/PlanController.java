package edu.indiana.p532.rpl.controller;

import edu.indiana.p532.rpl.domain.ActionStatus;
import edu.indiana.p532.rpl.domain.operational.plannode.Plan;
import edu.indiana.p532.rpl.domain.operational.plannode.PlanNodeEntity;
import edu.indiana.p532.rpl.domain.operational.plannode.ProposedAction;
import edu.indiana.p532.rpl.dto.CreatePlanRequest;
import edu.indiana.p532.rpl.dto.PlanNodeDto;
import edu.indiana.p532.rpl.dto.ReportNodeDto;
import edu.indiana.p532.rpl.engine.ActionStateMachineEngine;
import edu.indiana.p532.rpl.manager.PlanManager;
import edu.indiana.p532.rpl.manager.PlanReportManager;
import edu.indiana.p532.rpl.manager.ReportManager;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/plans")
public class PlanController {

    private final PlanManager planManager;
    private final ReportManager reportManager;
    private final PlanReportManager planReportManager;
    private final ActionStateMachineEngine stateMachineEngine;

    public PlanController(PlanManager planManager, ReportManager reportManager,
                           PlanReportManager planReportManager,
                           ActionStateMachineEngine stateMachineEngine) {
        this.planManager = planManager;
        this.reportManager = reportManager;
        this.planReportManager = planReportManager;
        this.stateMachineEngine = stateMachineEngine;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlanNodeDto create(@RequestBody CreatePlanRequest request) {
        return toDto(planManager.createPlan(request));
    }

    @GetMapping
    public List<PlanNodeDto> listTopLevel() {
        return planManager.listTopLevel().stream().map(this::toDto).toList();
    }

    @GetMapping("/{id}")
    public PlanNodeDto get(@PathVariable Long id) {
        return toDto(planManager.getPlanWithTree(id));
    }

    @GetMapping("/{id}/report")
    public List<ReportNodeDto> report(@PathVariable Long id,
                                      @RequestParam(required = false) String status) {
        if (status != null) {
            ActionStatus filter = ActionStatus.valueOf(status.toUpperCase());
            return planReportManager.generateReport(id, filter);
        }
        return reportManager.generateReport(id);
    }

    @PostMapping("/{id}/children")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> addChild(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String type = body.getOrDefault("type", "ACTION");
        String name = body.getOrDefault("name", "").trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Child name is required");
        planManager.addChild(id, name, type);
        return Map.of("added", true, "parentId", id, "name", name, "type", type);
    }

    private PlanNodeDto toDto(Plan plan) {
        List<PlanNodeDto> children = plan.getChildren().stream()
                .map(child -> child instanceof Plan subPlan
                        ? toDto(subPlan)
                        : actionToDto(child))
                .toList();
        String dateStr = plan.getTargetStartDate() != null ? plan.getTargetStartDate().toString() : null;
        return new PlanNodeDto(plan.getId(), plan.getName(), "PLAN",
                plan.getStatus().name(), List.of(), children, dateStr, null);
    }

    private PlanNodeDto actionToDto(PlanNodeEntity child) {
        // legalTransitions sourced from the state bean — no hardcoding; new states auto-appear
        List<String> transitions = stateMachineEngine.legalTransitions(child.getStatus().name());
        String dependsOn = child instanceof ProposedAction pa ? pa.getDependsOn() : null;
        return new PlanNodeDto(child.getId(), child.getName(), "ACTION",
                child.getStatus().name(), transitions, List.of(), null, dependsOn);
    }
}
