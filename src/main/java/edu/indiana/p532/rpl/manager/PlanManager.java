package edu.indiana.p532.rpl.manager;

import edu.indiana.p532.rpl.domain.knowledge.Protocol;
import edu.indiana.p532.rpl.domain.operational.plannode.Plan;
import edu.indiana.p532.rpl.domain.operational.plannode.ProposedAction;
import edu.indiana.p532.rpl.dto.CreatePlanRequest;
import edu.indiana.p532.rpl.engine.PlanInstantiationEngine;
import edu.indiana.p532.rpl.exception.ResourceNotFoundException;
import edu.indiana.p532.rpl.repository.PlanRepository;
import edu.indiana.p532.rpl.repository.ProtocolRepository;
import edu.indiana.p532.rpl.repository.ResourceAllocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PlanManager {

    private final PlanRepository planRepository;
    private final ProtocolRepository protocolRepository;
    private final PlanInstantiationEngine instantiationEngine;
    private final ResourceAllocationRepository allocationRepository;

    public PlanManager(PlanRepository planRepository,
                        ProtocolRepository protocolRepository,
                        PlanInstantiationEngine instantiationEngine,
                        ResourceAllocationRepository allocationRepository) {
        this.planRepository = planRepository;
        this.protocolRepository = protocolRepository;
        this.instantiationEngine = instantiationEngine;
        this.allocationRepository = allocationRepository;
    }

    @Transactional
    public Plan createPlan(CreatePlanRequest request) {
        if (request.sourceProtocolId() != null) {
            Protocol protocol = protocolRepository.findById(request.sourceProtocolId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Protocol not found: " + request.sourceProtocolId()));
            Plan plan = instantiationEngine.instantiate(
                    request.name(), protocol, request.targetStartDate());
            return planRepository.save(plan);
        }
        Plan plan = new Plan(request.name(), null, request.targetStartDate());
        return planRepository.save(plan);
    }

    @Transactional(readOnly = true)
    public Plan getPlanWithTree(Long id) {
        Plan plan = planRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + id));
        loadChildrenRecursively(plan);
        return plan;
    }

    @Transactional(readOnly = true)
    public List<Plan> listTopLevel() {
        List<Plan> plans = planRepository.findTopLevel();
        plans.forEach(this::loadChildrenRecursively);
        return plans;
    }

    @Transactional
    public void addChild(Long parentId, String name, String type) {
        Plan parent = planRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + parentId));
        if ("PLAN".equalsIgnoreCase(type)) {
            parent.addChild(new Plan(name, null, null));
        } else {
            parent.addChild(new ProposedAction(name, null, null, null, null));
        }
        planRepository.save(parent);
    }

    private void loadChildrenRecursively(Plan plan) {
        plan.getChildren().forEach(child -> {
            if (child instanceof Plan subPlan) {
                loadChildrenRecursively(subPlan);
            } else {
                child.loadAllocations(allocationRepository);
            }
        });
    }
}
