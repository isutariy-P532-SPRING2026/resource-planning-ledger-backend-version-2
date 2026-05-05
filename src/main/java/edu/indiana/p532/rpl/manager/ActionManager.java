package edu.indiana.p532.rpl.manager;

import edu.indiana.p532.rpl.domain.operational.AuditLogEntry;
import edu.indiana.p532.rpl.domain.operational.ImplementedAction;
import edu.indiana.p532.rpl.domain.operational.Suspension;
import edu.indiana.p532.rpl.domain.operational.plannode.ProposedAction;
import edu.indiana.p532.rpl.domain.operational.state.ActionContext;
import edu.indiana.p532.rpl.domain.operational.state.ActionContextCallback;
import edu.indiana.p532.rpl.domain.operational.state.ActionState;
import edu.indiana.p532.rpl.dto.ImplementActionRequest;
import edu.indiana.p532.rpl.dto.ResourceAllocationRequest;
import edu.indiana.p532.rpl.dto.SuspendRequest;
import edu.indiana.p532.rpl.engine.ActionStateMachineEngine;
import edu.indiana.p532.rpl.exception.ResourceNotFoundException;
import edu.indiana.p532.rpl.ledger.AssetLedgerEntryGenerator;
import edu.indiana.p532.rpl.repository.*;
import edu.indiana.p532.rpl.domain.knowledge.ResourceType;
import edu.indiana.p532.rpl.domain.operational.ResourceAllocation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ActionManager implements ActionContextCallback {

    private final ProposedActionRepository actionRepository;
    private final ImplementedActionRepository implementedActionRepository;
    private final SuspensionRepository suspensionRepository;
    private final ResourceAllocationRepository allocationRepository;
    private final ResourceTypeRepository resourceTypeRepository;
    private final AuditLogEntryRepository auditLogEntryRepository;
    private final ActionStateMachineEngine stateMachineEngine;
    private final LedgerManager ledgerManager;
    private final AssetLedgerEntryGenerator assetLedgerEntryGenerator;

    public ActionManager(ProposedActionRepository actionRepository,
                         ImplementedActionRepository implementedActionRepository,
                         SuspensionRepository suspensionRepository,
                         ResourceAllocationRepository allocationRepository,
                         ResourceTypeRepository resourceTypeRepository,
                         AuditLogEntryRepository auditLogEntryRepository,
                         ActionStateMachineEngine stateMachineEngine,
                         LedgerManager ledgerManager,
                         AssetLedgerEntryGenerator assetLedgerEntryGenerator) {
        this.actionRepository = actionRepository;
        this.implementedActionRepository = implementedActionRepository;
        this.suspensionRepository = suspensionRepository;
        this.allocationRepository = allocationRepository;
        this.resourceTypeRepository = resourceTypeRepository;
        this.auditLogEntryRepository = auditLogEntryRepository;
        this.stateMachineEngine = stateMachineEngine;
        this.ledgerManager = ledgerManager;
        this.assetLedgerEntryGenerator = assetLedgerEntryGenerator;
    }

    @Transactional
    public ProposedAction implement(Long actionId, ImplementActionRequest request) {
        ProposedAction action = load(actionId);
        ActionContext ctx = new ActionContext(action, this)
                .withImplementData(request.actualParty(), request.actualLocation(),
                        request.actualStart() != null ? request.actualStart() : Instant.now());
        resolveState(action).implement(ctx);
        audit("IMPLEMENT", action.getId());
        return actionRepository.save(action);
    }

    @Transactional
    public ProposedAction suspend(Long actionId, SuspendRequest request) {
        ProposedAction action = load(actionId);
        ActionContext ctx = new ActionContext(action, this)
                .withSuspensionReason(request.reason());
        resolveState(action).suspend(ctx, request.reason());
        audit("SUSPEND", action.getId());
        return actionRepository.save(action);
    }

    @Transactional
    public ProposedAction resume(Long actionId) {
        ProposedAction action = load(actionId);
        ActionContext ctx = new ActionContext(action, this);
        resolveState(action).resume(ctx);
        audit("RESUME", action.getId());
        return actionRepository.save(action);
    }

    @Transactional
    public ProposedAction complete(Long actionId) {
        ProposedAction action = load(actionId);
        ActionContext ctx = new ActionContext(action, this);
        resolveState(action).complete(ctx);
        audit("COMPLETE", action.getId());
        return actionRepository.save(action);
    }

    @Transactional
    public ProposedAction abandon(Long actionId) {
        ProposedAction action = load(actionId);
        ActionContext ctx = new ActionContext(action, this);
        resolveState(action).abandon(ctx);
        audit("ABANDON", action.getId());
        return actionRepository.save(action);
    }

    /**
     * Generic transition dispatcher. ActionApprovalController handles the
     * approval-workflow events (approve, reject, reopen, submitForApproval).
     */
    @Transactional
    public ProposedAction executeTransition(Long id, String event, Map<String, String> params) {
        return switch (event.toLowerCase()) {
            case "implement"  -> implement(id, new ImplementActionRequest(
                    params.getOrDefault("actualParty", ""),
                    params.getOrDefault("actualLocation", ""),
                    null));
            case "complete"   -> complete(id);
            case "suspend"    -> suspend(id, new SuspendRequest(params.getOrDefault("reason", "Suspended")));
            case "resume"     -> resume(id);
            case "abandon"    -> abandon(id);
            default -> throw new IllegalArgumentException("Unknown transition event: " + event);
        };
    }

    @Transactional
    public ResourceAllocation addAllocation(Long actionId, ResourceAllocationRequest request) {
        ProposedAction action = load(actionId);
        ResourceType rt = resourceTypeRepository.findById(request.resourceTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("ResourceType not found: " + request.resourceTypeId()));
        ResourceAllocation alloc = new ResourceAllocation(
                actionId, rt, request.quantity(), request.kind(),
                request.assetId(), request.timePeriod());
        return allocationRepository.save(alloc);
    }

    @Transactional(readOnly = true)
    public ProposedAction getById(Long id) {
        return load(id);
    }

    @Transactional(readOnly = true)
    public Optional<ImplementedAction> getImplementedAction(Long proposedActionId) {
        return implementedActionRepository.findByProposedActionId(proposedActionId);
    }

    @Transactional(readOnly = true)
    public List<ResourceAllocation> getAllocations(Long actionId) {
        List<ResourceAllocation> result = new ArrayList<>(
            allocationRepository.findByActionIdAndActionType(actionId, "PROPOSED_ACTION"));
        implementedActionRepository.findByProposedActionId(actionId).ifPresent(impl ->
            result.addAll(
                allocationRepository.findByActionIdAndActionType(impl.getId(), "IMPLEMENTED_ACTION"))
        );
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDetailAsMap(Long id) {
        return buildDetailMap(load(id));
    }

    private Map<String, Object> buildDetailMap(ProposedAction a) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id",              a.getId());
        map.put("name",            a.getName());
        map.put("status",          a.getStatus().name());
        map.put("party",           a.getParty()    != null ? a.getParty()    : "");
        map.put("timeRef",         a.getTimeRef()  != null ? a.getTimeRef()  : "");
        map.put("location",        a.getLocation() != null ? a.getLocation() : "");
        map.put("legalTransitions", stateMachineEngine.legalTransitions(a.getStatus().name()));
        map.put("dependsOn",       a.getDependsOn() != null ? a.getDependsOn() : "");

        Optional<ImplementedAction> implOpt =
                implementedActionRepository.findByProposedActionId(a.getId());

        List<ResourceAllocation> allocs = new ArrayList<>(
                allocationRepository.findByActionIdAndActionType(a.getId(), "PROPOSED_ACTION"));
        implOpt.ifPresent(impl -> allocs.addAll(
                allocationRepository.findByActionIdAndActionType(impl.getId(), "IMPLEMENTED_ACTION")));

        map.put("allocations", allocs.stream().map(al -> {
            Map<String, Object> am = new LinkedHashMap<>();
            am.put("id",               al.getId());
            am.put("resourceTypeName", al.getResourceType().getName());
            am.put("quantity",         al.getQuantity());
            am.put("kind",             al.getKind().name());
            am.put("assetId",          al.getAssetId()    != null ? al.getAssetId()    : "");
            am.put("timePeriod",       al.getTimePeriod() != null ? al.getTimePeriod() : "");
            return am;
        }).toList());

        map.put("implemented", implOpt.<Map<String, Object>>map(impl -> {
            Map<String, Object> im = new LinkedHashMap<>();
            im.put("actualParty",    impl.getActualParty()    != null ? impl.getActualParty()    : "");
            im.put("actualLocation", impl.getActualLocation() != null ? impl.getActualLocation() : "");
            im.put("actualStart",    impl.getActualStart()    != null ? impl.getActualStart().toString() : "");
            im.put("status",         impl.getStatus()         != null ? impl.getStatus().name()  : "");
            return im;
        }).orElse(null));

        return map;
    }

    // --- ActionContextCallback ---

    @Override
    public void onImplement(ProposedAction action, String actualParty, String actualLocation, Instant actualStart) {
        ImplementedAction impl = new ImplementedAction(action, actualStart, actualParty, actualLocation);
        implementedActionRepository.save(impl);
    }

    @Override
    public void onSuspend(ProposedAction action, String reason) {
        suspensionRepository.save(new Suspension(action, reason));
    }

    /**
     * Change 2: after the consumable ledger entries (via LedgerEngine), explicitly
     * call AssetLedgerEntryGenerator for SPECIFIC asset allocations.
     * AssetLedgerEntryGenerator.appliesTo() returns false so LedgerEngine skips it.
     */
    @Override
    public void onComplete(ProposedAction action) {
        ImplementedAction impl = implementedActionRepository.findByProposedActionId(action.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "No ImplementedAction found for action " + action.getId()));
        impl.setStatus(edu.indiana.p532.rpl.domain.ActionStatus.COMPLETED);
        implementedActionRepository.save(impl);
        ledgerManager.generateLedgerEntries(impl);         // consumables via LedgerEngine
        assetLedgerEntryGenerator.generateEntries(impl);   // SPECIFIC assets — Change 2
    }

    @Override
    public void onAbandon(ProposedAction action) {
        audit("ABANDON_DETAIL", action.getId());
    }

    @Override
    public boolean hasImplementation(ProposedAction action) {
        return implementedActionRepository.findByProposedActionId(action.getId()).isPresent();
    }

    private ProposedAction load(Long id) {
        return actionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProposedAction not found: " + id));
    }

    private ActionState resolveState(ProposedAction action) {
        return stateMachineEngine.resolve(action.getStateName());
    }

    private void audit(String event, Long actionId) {
        auditLogEntryRepository.save(new AuditLogEntry(event, null, null, actionId, null));
    }
}
