package edu.indiana.p532.rpl.visitor;

import edu.indiana.p532.rpl.domain.ActionStatus;
import edu.indiana.p532.rpl.domain.operational.plannode.Plan;
import edu.indiana.p532.rpl.domain.operational.plannode.PlanNodeVisitor;
import edu.indiana.p532.rpl.domain.operational.plannode.ProposedAction;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Visitor: computes the ratio of COMPLETED actions to total leaf actions.
 */
public class CompletionRatioVisitor implements PlanNodeVisitor {

    private int totalActions     = 0;
    private int completedActions = 0;

    @Override
    public void visitPlan(Plan plan) {}

    @Override
    public void visitAction(ProposedAction action) {
        totalActions++;
        if (action.getStatus() == ActionStatus.COMPLETED) {
            completedActions++;
        }
    }

    public Map<String, Object> getMetrics() {
        double ratio = totalActions == 0 ? 0.0
                : Math.round((double) completedActions / totalActions * 100.0) / 100.0;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("completedActions", completedActions);
        m.put("totalActions",     totalActions);
        m.put("completionRatio",  ratio);
        return m;
    }
}
