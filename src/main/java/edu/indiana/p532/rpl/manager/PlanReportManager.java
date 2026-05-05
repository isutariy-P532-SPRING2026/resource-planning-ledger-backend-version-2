package edu.indiana.p532.rpl.manager;

import edu.indiana.p532.rpl.domain.ActionStatus;
import edu.indiana.p532.rpl.domain.knowledge.ResourceType;
import edu.indiana.p532.rpl.domain.operational.plannode.Plan;
import edu.indiana.p532.rpl.domain.operational.plannode.PlanNode;
import edu.indiana.p532.rpl.domain.operational.plannode.PlanNodeEntity;
import edu.indiana.p532.rpl.domain.operational.plannode.ProposedAction;
import edu.indiana.p532.rpl.dto.ReportNodeDto;
import edu.indiana.p532.rpl.iterator.DepthFirstPlanIterator;
import edu.indiana.p532.rpl.iterator.FilteredPlanIterator;
import edu.indiana.p532.rpl.repository.ResourceTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates plan reports with optional status filtering (Change 3 / Week 2).
 *
 * When statusFilter is null: DepthFirstPlanIterator returns all nodes.
 * When statusFilter is non-null: FilteredPlanIterator yields only nodes
 * whose getStatus() matches the filter, while still traversing composites
 * so that deeply-nested matching leaves are not skipped.
 */
@Service
public class PlanReportManager {

    private final PlanManager planManager;
    private final ResourceTypeRepository resourceTypeRepository;

    public PlanReportManager(PlanManager planManager,
                             ResourceTypeRepository resourceTypeRepository) {
        this.planManager = planManager;
        this.resourceTypeRepository = resourceTypeRepository;
    }

    @Transactional(readOnly = true)
    public List<ReportNodeDto> generateReport(Long planId) {
        return generateReport(planId, null);
    }

    /**
     * Generates report rows for the given plan. With a non-null statusFilter,
     * uses FilteredPlanIterator so only nodes matching that status are included.
     */
    @Transactional(readOnly = true)
    public List<ReportNodeDto> generateReport(Long planId, ActionStatus statusFilter) {
        Plan plan = planManager.getPlanWithTree(planId);
        List<ResourceType> allResourceTypes = resourceTypeRepository.findAll();

        Iterator<PlanNode> iterator = statusFilter != null
                ? new FilteredPlanIterator(plan, node -> node.getStatus() == statusFilter)
                : new DepthFirstPlanIterator(plan);

        List<ReportNodeDto> report = new ArrayList<>();
        while (iterator.hasNext()) {
            PlanNode node = iterator.next();
            boolean isLeaf = !(node instanceof Plan);
            Map<String, BigDecimal> allocations = buildAllocationsMap(node, allResourceTypes);
            report.add(new ReportNodeDto(
                    node.getId(),
                    node.getName(),
                    isLeaf ? "ACTION" : "PLAN",
                    node.getStatus().name(),
                    computeDepth(node, plan),
                    allocations));
        }
        return report;
    }

    private Map<String, BigDecimal> buildAllocationsMap(PlanNode node, List<ResourceType> allResourceTypes) {
        Map<String, BigDecimal> map = new LinkedHashMap<>();
        for (ResourceType rt : allResourceTypes) {
            BigDecimal total = node.getTotalAllocatedQuantity(rt);
            if (total != null && total.compareTo(BigDecimal.ZERO) != 0) {
                map.put(rt.getName(), total);
            }
        }
        return map;
    }

    @Transactional(readOnly = true)
    public List<ProposedAction> getAllLeafActions(Long planId) {
        Plan plan = planManager.getPlanWithTree(planId);
        List<ProposedAction> actions = new ArrayList<>();
        Iterator<PlanNode> it = new DepthFirstPlanIterator(plan);
        while (it.hasNext()) {
            PlanNode node = it.next();
            if (node instanceof ProposedAction pa) actions.add(pa);
        }
        return actions;
    }

    @Transactional(readOnly = true)
    public List<ProposedAction> getActionsByStatus(Long planId, ActionStatus status) {
        Plan plan = planManager.getPlanWithTree(planId);
        List<ProposedAction> actions = new ArrayList<>();
        Iterator<PlanNode> it = new FilteredPlanIterator(plan, node -> node.getStatus() == status);
        while (it.hasNext()) {
            PlanNode node = it.next();
            if (node instanceof ProposedAction pa) actions.add(pa);
        }
        return actions;
    }

    private int computeDepth(PlanNode node, Plan root) {
        if (node instanceof Plan planNode) {
            if (planNode.getId() != null && planNode.getId().equals(root.getId())) return 0;
            Plan parent = planNode.getParent();
            return parent == null ? 0 : computeDepth(parent, root) + 1;
        }
        PlanNodeEntity entity = (PlanNodeEntity) node;
        Plan parent = entity.getParent();
        return parent == null ? 0 : computeDepth(parent, root) + 1;
    }
}
