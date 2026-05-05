package edu.indiana.p532.rpl.visitor;

import edu.indiana.p532.rpl.domain.operational.ResourceAllocation;
import edu.indiana.p532.rpl.domain.operational.plannode.Plan;
import edu.indiana.p532.rpl.domain.operational.plannode.PlanNodeVisitor;
import edu.indiana.p532.rpl.domain.operational.plannode.ProposedAction;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Visitor: accumulates total allocated quantity per resource type across all
 * leaf actions. Relies on loadedAllocations populated by PlanManager before
 * iteration — call getPlanWithTree() first.
 */
public class ResourceCostVisitor implements PlanNodeVisitor {

    private final Map<String, BigDecimal> totalByResource = new LinkedHashMap<>();

    @Override
    public void visitPlan(Plan plan) {}

    @Override
    public void visitAction(ProposedAction action) {
        for (ResourceAllocation alloc : action.getLoadedAllocations()) {
            String name = alloc.getResourceType().getName();
            totalByResource.merge(name, alloc.getQuantity(), BigDecimal::add);
        }
    }

    public Map<String, Object> getMetrics() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalByResource", totalByResource);
        return m;
    }
}
