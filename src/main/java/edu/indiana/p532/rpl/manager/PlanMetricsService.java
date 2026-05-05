package edu.indiana.p532.rpl.manager;

import edu.indiana.p532.rpl.domain.operational.plannode.Plan;
import edu.indiana.p532.rpl.domain.operational.plannode.PlanNode;
import edu.indiana.p532.rpl.iterator.DepthFirstPlanIterator;
import edu.indiana.p532.rpl.visitor.CompletionRatioVisitor;
import edu.indiana.p532.rpl.visitor.ResourceCostVisitor;
import edu.indiana.p532.rpl.visitor.RiskScoreVisitor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs the three Week-2 visitors in a single depth-first pass and returns
 * a combined metrics map (Change 4 / Week 2).
 * getPlanWithTree() pre-loads allocations so ResourceCostVisitor can use loadedAllocations.
 */
@Service
public class PlanMetricsService {

    private final PlanManager planManager;

    public PlanMetricsService(PlanManager planManager) {
        this.planManager = planManager;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMetrics(Long planId) {
        Plan plan = planManager.getPlanWithTree(planId);

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
