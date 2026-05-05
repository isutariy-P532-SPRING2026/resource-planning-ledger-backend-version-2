package edu.indiana.p532.rpl.iterator;

import edu.indiana.p532.rpl.domain.operational.plannode.Plan;
import edu.indiana.p532.rpl.domain.operational.plannode.PlanNode;
import edu.indiana.p532.rpl.domain.operational.plannode.PlanNodeEntity;
import edu.indiana.p532.rpl.repository.ResourceAllocationRepository;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Iterator pattern variant (Week 2): depth-first pre-order traversal that loads
 * leaf allocations lazily — only when a leaf is about to be returned, not upfront.
 *
 * Contrast with DepthFirstPlanIterator (which requires PlanManager to pre-load all
 * allocations via loadChildrenRecursively before iteration begins).
 * LazySubtreeIterator is more memory-efficient when the caller may stop early or
 * needs fine-grained control over which leaves get their allocations loaded.
 *
 * Must be used inside an active @Transactional boundary so JPA lazy collections
 * on child Plan nodes can be accessed.
 */
public class LazySubtreeIterator implements Iterator<PlanNode> {

    private final ResourceAllocationRepository allocationRepository;
    private final Deque<PlanNode> stack = new ArrayDeque<>();

    public LazySubtreeIterator(PlanNode root, ResourceAllocationRepository allocationRepository) {
        this.allocationRepository = allocationRepository;
        stack.push(root);
    }

    @Override
    public boolean hasNext() { return !stack.isEmpty(); }

    @Override
    public PlanNode next() {
        if (!hasNext()) throw new NoSuchElementException();
        PlanNode node = stack.pop();
        if (node instanceof Plan plan) {
            List<PlanNodeEntity> children = plan.getChildren();
            // push in reverse so first child is processed first
            for (int i = children.size() - 1; i >= 0; i--) {
                stack.push(children.get(i));
            }
        } else {
            // Lazy: load allocations only at the moment this leaf is yielded
            node.loadAllocations(allocationRepository);
        }
        return node;
    }
}
