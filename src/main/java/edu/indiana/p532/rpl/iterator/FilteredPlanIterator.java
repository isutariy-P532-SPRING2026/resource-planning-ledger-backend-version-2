package edu.indiana.p532.rpl.iterator;

import edu.indiana.p532.rpl.domain.operational.plannode.PlanNode;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Predicate;

/**
 * Iterator pattern extension (Change 3 / Week 2): decorates DepthFirstPlanIterator
 * with a predicate filter. Composite Plan nodes are always traversed internally
 * (their children are visited) even when filtered out — so no subtree is skipped.
 */
public class FilteredPlanIterator implements Iterator<PlanNode> {

    private final DepthFirstPlanIterator underlying;
    private final Predicate<PlanNode> predicate;
    private PlanNode next;

    public FilteredPlanIterator(PlanNode root, Predicate<PlanNode> predicate) {
        this.underlying = new DepthFirstPlanIterator(root);
        this.predicate  = predicate;
        advance();
    }

    private void advance() {
        next = null;
        while (underlying.hasNext()) {
            PlanNode candidate = underlying.next();
            if (predicate.test(candidate)) {
                next = candidate;
                break;
            }
        }
    }

    @Override
    public boolean hasNext() { return next != null; }

    @Override
    public PlanNode next() {
        if (!hasNext()) throw new NoSuchElementException();
        PlanNode result = next;
        advance();
        return result;
    }
}
