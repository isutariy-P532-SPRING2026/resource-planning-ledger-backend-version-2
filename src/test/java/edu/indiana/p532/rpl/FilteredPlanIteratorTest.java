package edu.indiana.p532.rpl;

import edu.indiana.p532.rpl.domain.operational.plannode.Plan;
import edu.indiana.p532.rpl.domain.operational.plannode.PlanNode;
import edu.indiana.p532.rpl.domain.operational.plannode.ProposedAction;
import edu.indiana.p532.rpl.iterator.FilteredPlanIterator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class FilteredPlanIteratorTest {

    private ProposedAction leaf(String name) {
        return new ProposedAction(name, null, null, null, null);
    }

    @Test
    void filter_leavesOnly_skipsPlanComposites() {
        Plan root = new Plan("root", null, null);
        Plan sub  = new Plan("sub",  null, null);
        ProposedAction a = leaf("A");
        ProposedAction b = leaf("B");
        sub.addChild(b);
        root.addChild(a);
        root.addChild(sub);

        FilteredPlanIterator it = new FilteredPlanIterator(root,
                node -> node instanceof ProposedAction);
        List<String> names = new ArrayList<>();
        while (it.hasNext()) names.add(it.next().getName());

        assertEquals(List.of("A", "B"), names);
    }

    @Test
    void filter_plansOnly_skipsLeaves() {
        Plan root = new Plan("root", null, null);
        Plan sub  = new Plan("sub",  null, null);
        sub.addChild(leaf("A"));
        root.addChild(sub);
        root.addChild(leaf("B"));

        FilteredPlanIterator it = new FilteredPlanIterator(root,
                node -> node instanceof Plan);
        List<String> names = new ArrayList<>();
        while (it.hasNext()) names.add(it.next().getName());

        assertEquals(List.of("root", "sub"), names);
    }

    @Test
    void filter_alwaysFalse_returnsNoNodes() {
        Plan root = new Plan("root", null, null);
        root.addChild(leaf("A"));

        FilteredPlanIterator it = new FilteredPlanIterator(root, node -> false);
        assertFalse(it.hasNext());
    }

    @Test
    void filter_exhausted_throwsNoSuchElementException() {
        FilteredPlanIterator it = new FilteredPlanIterator(leaf("only"), node -> false);
        assertThrows(NoSuchElementException.class, it::next);
    }

    @Test
    void filter_deeplyNested_findsLeafInSubtree() {
        Plan root = new Plan("root", null, null);
        Plan sub1 = new Plan("sub1", null, null);
        Plan sub2 = new Plan("sub2", null, null);
        ProposedAction deep = leaf("deep");
        sub2.addChild(deep);
        sub1.addChild(sub2);
        root.addChild(sub1);

        FilteredPlanIterator it = new FilteredPlanIterator(root,
                node -> node instanceof ProposedAction);
        assertTrue(it.hasNext());
        assertEquals("deep", it.next().getName());
        assertFalse(it.hasNext());
    }
}
