package edu.indiana.p532.rpl;

import edu.indiana.p532.rpl.domain.operational.plannode.Plan;
import edu.indiana.p532.rpl.domain.operational.plannode.PlanNode;
import edu.indiana.p532.rpl.domain.operational.plannode.ProposedAction;
import edu.indiana.p532.rpl.iterator.LazySubtreeIterator;
import edu.indiana.p532.rpl.repository.ResourceAllocationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LazySubtreeIteratorTest {

    @Mock
    private ResourceAllocationRepository allocationRepository;

    private ProposedAction leaf(String name) {
        return new ProposedAction(name, null, null, null, null);
    }

    @Test
    void iterator_flatPlan_returnsAllNodesDepthFirst() {
        Plan root = new Plan("root", null, null);
        ProposedAction a = leaf("A");
        ProposedAction b = leaf("B");
        root.addChild(a);
        root.addChild(b);

        when(allocationRepository.findByActionIdAndActionType(any(), any())).thenReturn(List.of());

        LazySubtreeIterator it = new LazySubtreeIterator(root, allocationRepository);
        List<String> names = new ArrayList<>();
        while (it.hasNext()) names.add(it.next().getName());

        assertEquals(List.of("root", "A", "B"), names);
    }

    @Test
    void iterator_loadsAllocationsLazily_onlyWhenLeafIsReturned() {
        Plan root = new Plan("root", null, null);
        ProposedAction a = leaf("A");
        ProposedAction b = leaf("B");
        root.addChild(a);
        root.addChild(b);

        when(allocationRepository.findByActionIdAndActionType(any(), any())).thenReturn(List.of());

        LazySubtreeIterator it = new LazySubtreeIterator(root, allocationRepository);

        it.next(); // root (Plan) — no allocation call
        verify(allocationRepository, never()).findByActionIdAndActionType(any(), any());

        it.next(); // A (leaf) — allocation loaded now
        verify(allocationRepository, times(1)).findByActionIdAndActionType(any(), any());

        it.next(); // B (leaf)
        verify(allocationRepository, times(2)).findByActionIdAndActionType(any(), any());
    }

    @Test
    void iterator_nestedPlan_traversesDepthFirst() {
        Plan root = new Plan("root", null, null);
        Plan sub  = new Plan("sub",  null, null);
        ProposedAction x = leaf("X");
        sub.addChild(x);
        root.addChild(sub);
        root.addChild(leaf("Y"));

        when(allocationRepository.findByActionIdAndActionType(any(), any())).thenReturn(List.of());

        LazySubtreeIterator it = new LazySubtreeIterator(root, allocationRepository);
        List<String> names = new ArrayList<>();
        while (it.hasNext()) names.add(it.next().getName());

        assertEquals(List.of("root", "sub", "X", "Y"), names);
    }

    @Test
    void iterator_exhausted_throwsNoSuchElementException() {
        LazySubtreeIterator it = new LazySubtreeIterator(leaf("only"), allocationRepository);
        when(allocationRepository.findByActionIdAndActionType(any(), any())).thenReturn(List.of());
        it.next();
        assertThrows(NoSuchElementException.class, it::next);
    }
}
