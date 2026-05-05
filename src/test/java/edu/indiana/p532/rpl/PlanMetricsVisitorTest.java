package edu.indiana.p532.rpl;

import edu.indiana.p532.rpl.domain.ActionStatus;
import edu.indiana.p532.rpl.domain.AllocationKind;
import edu.indiana.p532.rpl.domain.ResourceKind;
import edu.indiana.p532.rpl.domain.knowledge.ResourceType;
import edu.indiana.p532.rpl.domain.operational.ResourceAllocation;
import edu.indiana.p532.rpl.domain.operational.plannode.Plan;
import edu.indiana.p532.rpl.domain.operational.plannode.PlanNode;
import edu.indiana.p532.rpl.domain.operational.plannode.ProposedAction;
import edu.indiana.p532.rpl.iterator.DepthFirstPlanIterator;
import edu.indiana.p532.rpl.visitor.CompletionRatioVisitor;
import edu.indiana.p532.rpl.visitor.ResourceCostVisitor;
import edu.indiana.p532.rpl.visitor.RiskScoreVisitor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlanMetricsVisitorTest {

    private ProposedAction leaf(String name) {
        return new ProposedAction(name, null, null, null, null);
    }

    private ProposedAction leafWithStatus(String name, ActionStatus status) {
        ProposedAction a = leaf(name);
        a.setStateName(status.name());
        return a;
    }

    private void applyAll(Plan root, CompletionRatioVisitor c, ResourceCostVisitor r, RiskScoreVisitor s) {
        DepthFirstPlanIterator it = new DepthFirstPlanIterator(root);
        while (it.hasNext()) {
            PlanNode node = it.next();
            node.accept(c);
            node.accept(r);
            node.accept(s);
        }
    }

    // ── CompletionRatioVisitor ────────────────────────────────────────────────

    @Test
    void completionRatio_noActions_ratioIsZero() {
        Plan root = new Plan("root", null, null);
        CompletionRatioVisitor v = new CompletionRatioVisitor();
        new DepthFirstPlanIterator(root).forEachRemaining(n -> n.accept(v));
        Map<String, Object> m = v.getMetrics();
        assertEquals(0, m.get("totalActions"));
        assertEquals(0.0, m.get("completionRatio"));
    }

    @Test
    void completionRatio_twoCompletedOutOfFour_isHalf() {
        Plan root = new Plan("root", null, null);
        root.addChild(leafWithStatus("A", ActionStatus.COMPLETED));
        root.addChild(leafWithStatus("B", ActionStatus.COMPLETED));
        root.addChild(leafWithStatus("C", ActionStatus.PROPOSED));
        root.addChild(leafWithStatus("D", ActionStatus.IN_PROGRESS));

        CompletionRatioVisitor v = new CompletionRatioVisitor();
        new DepthFirstPlanIterator(root).forEachRemaining(n -> n.accept(v));
        Map<String, Object> m = v.getMetrics();

        assertEquals(2, m.get("completedActions"));
        assertEquals(4, m.get("totalActions"));
        assertEquals(0.5, m.get("completionRatio"));
    }

    // ── ResourceCostVisitor ───────────────────────────────────────────────────

    @Test
    void resourceCost_noAllocations_emptyMap() {
        Plan root = new Plan("root", null, null);
        root.addChild(leaf("A")); // loadedAllocations empty by default

        ResourceCostVisitor v = new ResourceCostVisitor();
        new DepthFirstPlanIterator(root).forEachRemaining(n -> n.accept(v));
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> byResource = (Map<String, BigDecimal>) v.getMetrics().get("totalByResource");
        assertTrue(byResource.isEmpty());
    }

    @Test
    void resourceCost_twoActionsWithAllocations_sumsByResourceType() {
        ResourceType steel = new ResourceType("Steel", ResourceKind.CONSUMABLE, "kg");
        ResourceAllocation alloc1 = new ResourceAllocation(1L, steel, new BigDecimal("5"),
                AllocationKind.GENERAL, null, null);
        ResourceAllocation alloc2 = new ResourceAllocation(2L, steel, new BigDecimal("3"),
                AllocationKind.GENERAL, null, null);

        ProposedAction a1 = leaf("A1");
        a1.setLoadedAllocations(List.of(alloc1));
        ProposedAction a2 = leaf("A2");
        a2.setLoadedAllocations(List.of(alloc2));

        Plan root = new Plan("root", null, null);
        root.addChild(a1);
        root.addChild(a2);

        ResourceCostVisitor v = new ResourceCostVisitor();
        new DepthFirstPlanIterator(root).forEachRemaining(n -> n.accept(v));
        @SuppressWarnings("unchecked")
        Map<String, BigDecimal> byResource = (Map<String, BigDecimal>) v.getMetrics().get("totalByResource");
        assertEquals(new BigDecimal("8"), byResource.get("Steel"));
    }

    // ── RiskScoreVisitor ──────────────────────────────────────────────────────

    @Test
    void riskScore_allCompleted_riskIsLow() {
        Plan root = new Plan("root", null, null);
        root.addChild(leafWithStatus("A", ActionStatus.COMPLETED));
        root.addChild(leafWithStatus("B", ActionStatus.COMPLETED));

        RiskScoreVisitor v = new RiskScoreVisitor();
        new DepthFirstPlanIterator(root).forEachRemaining(n -> n.accept(v));
        Map<String, Object> m = v.getMetrics();

        assertEquals("LOW",  m.get("riskLevel"));
        assertEquals(0,      m.get("riskScore"));
    }

    @Test
    void riskScore_allAbandoned_riskIsHigh() {
        Plan root = new Plan("root", null, null);
        root.addChild(leafWithStatus("A", ActionStatus.ABANDONED));
        root.addChild(leafWithStatus("B", ActionStatus.ABANDONED));

        RiskScoreVisitor v = new RiskScoreVisitor();
        new DepthFirstPlanIterator(root).forEachRemaining(n -> n.accept(v));
        Map<String, Object> m = v.getMetrics();

        assertEquals("HIGH", m.get("riskLevel"));
        assertEquals(2,      m.get("abandonedActions"));
    }

    @Test
    void riskScore_mixedStatuses_correctCounts() {
        Plan root = new Plan("root", null, null);
        root.addChild(leafWithStatus("A", ActionStatus.COMPLETED));
        root.addChild(leafWithStatus("B", ActionStatus.SUSPENDED));
        root.addChild(leafWithStatus("C", ActionStatus.PROPOSED));

        RiskScoreVisitor v = new RiskScoreVisitor();
        new DepthFirstPlanIterator(root).forEachRemaining(n -> n.accept(v));
        Map<String, Object> m = v.getMetrics();

        assertEquals(1, m.get("suspendedActions"));
        assertEquals(1, m.get("unstartedActions"));
        assertEquals(3, m.get("totalActions"));
    }
}
