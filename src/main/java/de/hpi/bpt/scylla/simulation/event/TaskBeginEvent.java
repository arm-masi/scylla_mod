package de.hpi.bpt.scylla.simulation.event;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import co.paralleluniverse.fibers.SuspendExecution;
import de.hpi.bpt.scylla.exception.ScyllaRuntimeException;
import de.hpi.bpt.scylla.logger.ProcessNodeInfo;
import de.hpi.bpt.scylla.logger.ProcessNodeTransitionType;
import de.hpi.bpt.scylla.model.process.ProcessModel;
import de.hpi.bpt.scylla.model.process.node.TaskType;
import de.hpi.bpt.scylla.plugin_type.simulation.event.TaskBeginEventPluggable;
import de.hpi.bpt.scylla.simulation.*;
import de.hpi.bpt.scylla.simulation.utils.DateTimeUtils;
import de.hpi.bpt.scylla.simulation.utils.SimulationUtils;
import desmoj.core.simulator.Model;
import desmoj.core.simulator.TimeInstant;
import desmoj.core.simulator.TimeSpan;

public class TaskBeginEvent extends TaskEvent {

    // ✅ Durata personalizzata, calcolata dai plugin
    private Double customDuration = null;

    public TaskBeginEvent(Model owner, String source, TimeInstant simulationTimeOfSource,
                          ProcessSimulationComponents desmojObjects, ProcessInstance processInstance, int nodeId) {
        super(owner, source, simulationTimeOfSource, desmojObjects, processInstance, nodeId);
    }

    public void setCustomDuration(double duration) {
        this.customDuration = duration;
    }

    public Double getCustomDuration() {
        return this.customDuration;
    }

    @Override
    public void eventRoutine(ProcessInstance processInstance) throws SuspendExecution {
        super.eventRoutine(processInstance);

        SimulationModel model = (SimulationModel) getModel();
        TimeInstant currentSimulationTime = model.presentTime();
        ProcessModel processModel = processInstance.getProcessModel();

        // ✅ Log del tipo di task (SERVICE, SEND, ecc.)
        if (!handleTaskTypeLogging(processModel)) return;

        try {
            // ✅ Esegui plugin associati (es. resourceCost)
            TaskBeginEventPluggable.runPlugins(this, processInstance);

            // ✅ Calcolo durata
            double baseDuration = pSimComponents.getDistributionSample(nodeId);
            double effectiveDuration = (customDuration != null) ? customDuration : baseDuration;
            TimeUnit unit = pSimComponents.getDistributionTimeUnit(nodeId);

            TaskTerminateEvent terminateEvent = new TaskTerminateEvent(model, source, currentSimulationTime, pSimComponents,
                    processInstance, nodeId);
            terminateEvent.setCustomDuration(effectiveDuration); // utile per log/debug

            System.out.printf("✅ [TaskBeginEvent] durata effettiva = %.3f sec, task = %s%n", effectiveDuration, displayName);

            // ✅ Programma l’evento di terminazione
            TimeSpan timeSpan = new TimeSpan(effectiveDuration, unit);
            ResourceObjectTuple tuple = processInstance.getAssignedResources().get(source);

            TimeInstant nextEventTime = DateTimeUtils.getTaskTerminationTime(timeSpan, currentSimulationTime, tuple, terminateEvent);
            timeSpan = new TimeSpan(nextEventTime.getTimeAsDouble() - currentSimulationTime.getTimeAsDouble());

            int index = getNewEventIndex();
            nextEventMap.put(index, terminateEvent);
            timeSpanToNextEventMap.put(index, timeSpan);

            scheduleNextEvents();

        } catch (ScyllaRuntimeException e) {
            System.err.println("❌ Errore in TaskBeginEvent (nodeId=" + nodeId + "): " + e.getMessage());
            e.printStackTrace();
            SimulationUtils.abort(model, processInstance, nodeId, traceIsOn());
        }
    }

    /**
     * Logging iniziale del tipo di task (SERVICE, SEND, USER, ecc.)
     */
    private boolean handleTaskTypeLogging(ProcessModel processModel) {
        TaskType type = processModel.getTasks().get(nodeId);
        ProcessModel subProcess = processModel.getSubProcesses().get(nodeId);

        String message;
        if (subProcess != null) {
            message = "Begin Subprocess: " + displayName;
        } else if (type != null) {
            message = "Begin " + type.name() + " Task: " + displayName;
        } else {
            SimulationUtils.sendElementNotSupportedTraceNote((SimulationModel) getModel(), processModel, displayName, nodeId);
            SimulationUtils.abort((SimulationModel) getModel(), null, nodeId, traceIsOn());
            return false;
        }

        sendTraceNote(message);
        return true;
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

        ProcessNodeInfo info = new ProcessNodeInfo(
                nodeId,
                SimulationUtils.getProcessScopeNodeId(processInstance.getProcessModel(), nodeId),
                source,
                timestamp,
                taskName,
                resources,
                ProcessNodeTransitionType.BEGIN
        );

        ((SimulationModel) getModel()).addNodeInfo(processInstance.getProcessModel(), processInstance, info);
    }
}
