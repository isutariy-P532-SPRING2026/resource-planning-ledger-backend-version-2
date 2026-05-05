package edu.indiana.p532.rpl.visitor;

import edu.indiana.p532.rpl.domain.ActionStatus;
import edu.indiana.p532.rpl.domain.operational.plannode.Plan;
import edu.indiana.p532.rpl.domain.operational.plannode.PlanNodeVisitor;
import edu.indiana.p532.rpl.domain.operational.plannode.ProposedAction;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Visitor: scores plan risk from action status distribution.
 *
 * Scoring weights (per action):
 *   ABANDONED            → 3 pts (most severe — work lost)
 *   SUSPENDED            → 2 pts (blocked, uncertain)
 *   PROPOSED (unstarted) → 1 pt  (not yet begun)
 *   Others (COMPLETED, IN_PROGRESS, PENDING_APPROVAL, REOPENED) → 0 pts
 *
 * riskLevel: LOW  (ratio < 0.25)
 *            MEDIUM (0.25 ≤ ratio < 0.60)
 *            HIGH (ratio ≥ 0.60)
 */
public class RiskScoreVisitor implements PlanNodeVisitor {

    private int total     = 0;
    private int abandoned = 0;
    private int suspended = 0;
    private int proposed  = 0;

    @Override
    public void visitPlan(Plan plan) {}

    @Override
    public void visitAction(ProposedAction action) {
        total++;
        switch (action.getStatus()) {
            case ABANDONED -> abandoned++;
            case SUSPENDED -> suspended++;
            case PROPOSED  -> proposed++;
            default        -> { /* COMPLETED, IN_PROGRESS, PENDING_APPROVAL, REOPENED = 0 risk */ }
        }
    }

    public Map<String, Object> getMetrics() {
        int rawScore = abandoned * 3 + suspended * 2 + proposed;
        int maxScore = total * 3;
        double ratio = (total == 0 || maxScore == 0) ? 0.0 : (double) rawScore / maxScore;
        String level = ratio < 0.25 ? "LOW" : ratio < 0.60 ? "MEDIUM" : "HIGH";

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("riskLevel",        level);
        m.put("riskScore",        rawScore);
        m.put("abandonedActions", abandoned);
        m.put("suspendedActions", suspended);
        m.put("unstartedActions", proposed);
        m.put("totalActions",     total);
        return m;
    }
}
