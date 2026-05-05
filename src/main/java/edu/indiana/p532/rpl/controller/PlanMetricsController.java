package edu.indiana.p532.rpl.controller;

import edu.indiana.p532.rpl.domain.operational.plannode.Plan;
import edu.indiana.p532.rpl.domain.operational.plannode.PlanNode;
import edu.indiana.p532.rpl.iterator.DepthFirstPlanIterator;
import edu.indiana.p532.rpl.manager.PlanManager;
import edu.indiana.p532.rpl.visitor.CompletionRatioVisitor;
import edu.indiana.p532.rpl.visitor.ResourceCostVisitor;
import edu.indiana.p532.rpl.visitor.RiskScoreVisitor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /api/plans/{id}/metrics  →  combined metrics from all three Week-2 visitors.
 * Combines the Iterator traversal pattern with Visitor computation in one pass each.
 * getPlanWithTree() pre-loads allocations so ResourceCostVisitor can use loadedAllocations.
 */
@RestController
@RequestMapping("/api/plans")
public class PlanMetricsController {

    private final PlanManager planManager;

    public PlanMetricsController(PlanManager planManager) {
        this.planManager = planManager;
    }

    @Transactional(readOnly = true)
    @GetMapping("/{id}/metrics")
    public Map<String, Object> getMetrics(@PathVariable Long id) {
        Plan plan = planManager.getPlanWithTree(id);

        CompletionRatioVisitor completion = new CompletionRatioVisitor();
        ResourceCostVisitor    cost       = new ResourceCostVisitor();
        RiskScoreVisitor       risk       = new RiskScoreVisitor();

        DepthFirstPlanIterator it = new DepthFirstPlanIterator(plan);
        while (it.hasNext()) {
            PlanNode node = it.next();
            node.accept(completion);
            node.accept(cost);
            node.accept(risk);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("completion",   completion.getMetrics());
        result.put("resourceCost", cost.getMetrics());
        result.put("risk",         risk.getMetrics());
        return result;
    }
}
