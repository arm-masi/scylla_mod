package de.hpi.bpt.scylla.simulation.event;

import java.util.*;

import co.paralleluniverse.fibers.SuspendExecution;
import de.hpi.bpt.scylla.exception.ScyllaRuntimeException;
import de.hpi.bpt.scylla.exception.ScyllaValidationException;
import de.hpi.bpt.scylla.logger.DebugLogger;
import de.hpi.bpt.scylla.logger.ProcessNodeInfo;
import de.hpi.bpt.scylla.logger.ProcessNodeTransitionType;
import de.hpi.bpt.scylla.model.process.ProcessModel;
import de.hpi.bpt.scylla.model.process.graph.exception.NodeNotFoundException;
import de.hpi.bpt.scylla.model.process.node.TaskType;
import de.hpi.bpt.scylla.plugin_type.simulation.event.TaskTerminateEventPluggable;
import de.hpi.bpt.scylla.simulation.*;
import de.hpi.bpt.scylla.simulation.utils.DateTimeUtils;
import de.hpi.bpt.scylla.simulation.utils.SimulationUtils;
import desmoj.core.simulator.Model;
import desmoj.core.simulator.TimeInstant;
import desmoj.core.simulator.TimeSpan;

public class TaskTerminateEvent extends TaskEvent {

    private Double customDuration = null;

    public TaskTerminateEvent(Model owner, String source, TimeInstant simulationTimeOfSource,
                              ProcessSimulationComponents desmojObjects, ProcessInstance processInstance, int nodeId) {
        super(owner, source, simulationTimeOfSource, desmojObjects, processInstance, nodeId);
    }

    public void setCustomDuration(Double duration) {
        this.customDuration = duration;
    }

    public Double getCustomDuration() {
        return this.customDuration;
    }

    @Override
    public void eventRoutine(ProcessInstance processInstance) throws SuspendExecution {
        super.eventRoutine(processInstance);
        SimulationModel model = (SimulationModel) getModel();
        ProcessModel processModel = processInstance.getProcessModel();

        try {
            String message = getTaskEndMessage(processModel);
            if (message == null) return;

            sendTraceNote(message);

            // ✅ Rilascia risorse e gestisci code
            model.getResourceManager().releaseResourcesAndScheduleQueuedEvents(this);

            // ✅ Ottieni nodo successivo
            Set<Integer> idsOfNextNodes = processModel.getIdsOfNextNodes(nodeId);
            if (idsOfNextNodes.size() != 1) {
                throw new ScyllaValidationException("Task " + nodeId + " does not have exactly one successor.");
            }

            Integer nextNodeId = idsOfNextNodes.iterator().next();
            List<ScyllaEvent> events = SimulationUtils.createEventsForNextNode(
                    this, pSimComponents, processInstance, nextNodeId);

            TimeSpan timeSpan = new TimeSpan(0);  // immediatamente

            for (ScyllaEvent event : events) {
                int index = getNewEventIndex();
                nextEventMap.put(index, event);
                timeSpanToNextEventMap.put(index, timeSpan);
            }

            // ✅ Plugin terminate
            TaskTerminateEventPluggable.runPlugins(this, processInstance);

            scheduleNextEvents();

        } catch (NodeNotFoundException | ScyllaValidationException | ScyllaRuntimeException e) {
            DebugLogger.error(e.getMessage());
            e.printStackTrace();
            SimulationUtils.abort(model, processInstance, nodeId, traceIsOn());
        }
    }

    private String getTaskEndMessage(ProcessModel processModel) {
        TaskType type = processModel.getTasks().get(nodeId);
        ProcessModel subProcess = processModel.getSubProcesses().get(nodeId);

        if (subProcess != null) {
            return "End of Subprocess: " + displayName;
        }
        if (type != null) {
            return "End of " + type.name() + " Task: " + displayName;
        }

        SimulationUtils.sendElementNotSupportedTraceNote((SimulationModel) getModel(), processModel, displayName, nodeId);
        SimulationUtils.abort((SimulationModel) getModel(), null, nodeId, traceIsOn());
        return null;
    }

    @Override
    protected void addToLog(ProcessInstance processInstance) {
        long timestamp = Math.round(getModel().presentTime().getTimeRounded(DateTimeUtils.getReferenceTimeUnit()));
        String taskName = displayName;

        Set<String> resources = new HashSet<>();
        Set<ResourceObject> resourceObjects = processInstance.getAssignedResources().get(source).getResourceObjects();
        for (ResourceObject res : resourceObjects) {
            resources.add(res.getResourceType() + "_" + res.getId());
        }

        System.out.printf("📌 [TaskTerminateEvent] Durata effettiva = %.3f sec | Task = %s%n",
                customDuration != null ? customDuration : 0.0, taskName);

        ProcessModel processModel = processInstance.getProcessModel();
        SimulationModel model = (SimulationModel) getModel();

        if (!alreadyCanceled(model)) {
            ProcessNodeInfo info = new ProcessNodeInfo(
                    nodeId,
                    SimulationUtils.getProcessScopeNodeId(processModel, nodeId),
                    source,
                    timestamp,
                    taskName,
                    resources,
                    ProcessNodeTransitionType.TERMINATE
            );
            model.addNodeInfo(processModel, processInstance, info);
        }
    }

    private boolean alreadyCanceled(SimulationModel model) {
        Collection<Map<Integer, List<ProcessNodeInfo>>> allProcesses = model.getProcessNodeInfos().values();
        for (Map<Integer, List<ProcessNodeInfo>> process : allProcesses) {
            List<ProcessNodeInfo> currentProcess = process.get(processInstance.getId());
            for (ProcessNodeInfo task : currentProcess) {
                if (task.getId().equals(nodeId) &&
                        task.getTransition().equals(ProcessNodeTransitionType.CANCEL)) {
                    return true;
                }
            }
        }
        return false;
    }
}
