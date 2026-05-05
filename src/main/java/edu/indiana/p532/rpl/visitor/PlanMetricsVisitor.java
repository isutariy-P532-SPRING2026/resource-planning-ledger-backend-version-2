package edu.indiana.p532.rpl.visitor;

import edu.indiana.p532.rpl.domain.operational.plannode.Plan;
import edu.indiana.p532.rpl.domain.operational.plannode.PlanNodeVisitor;
import edu.indiana.p532.rpl.domain.operational.plannode.ProposedAction;

/**
 * Replaced in Week 2 by CompletionRatioVisitor, ResourceCostVisitor, RiskScoreVisitor.
 * Kept as a compile-safe stub so existing call sites fail at compile time if still present.
 */
@Deprecated
public class PlanMetricsVisitor implements PlanNodeVisitor {
    @Override public void visitPlan(Plan plan) {}
    @Override public void visitAction(ProposedAction action) {}
}
