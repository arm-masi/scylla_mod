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

    // ✅ campo opzionale per durate personalizzate (plugin)
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

        if (!handleTaskTypeLogging(processModel)) return;

        try {
            // ✅ Prima esegui i plugin (se presenti)
            TaskBeginEventPluggable.runPlugins(this, processInstance);

            // ✅ Calcola durata base da distribuzione
            double baseDuration = pSimComponents.getDistributionSample(nodeId);
            // ✅ Se il plugin ha impostato una durata personalizzata, usala
            double effectiveDuration = (customDuration != null) ? customDuration : baseDuration;
            TimeUnit unit = pSimComponents.getDistributionTimeUnit(nodeId);

            TaskTerminateEvent terminateEvent = new TaskTerminateEvent(model, source, currentSimulationTime, pSimComponents,
                    processInstance, nodeId);
            terminateEvent.setCustomDuration(effectiveDuration); // solo per logging/analisi

            System.out.println("✅ [TaskBeginEvent] durata effettiva = " + effectiveDuration + " sec, hash=" + terminateEvent.hashCode());

            ScyllaEvent event = terminateEvent;
            TimeSpan timeSpan = new TimeSpan(effectiveDuration, unit);

            ResourceObjectTuple tuple = processInstance.getAssignedResources().get(source);
            TimeInstant nextEventTime = DateTimeUtils.getTaskTerminationTime(timeSpan, currentSimulationTime, tuple, event);
            timeSpan = new TimeSpan(nextEventTime.getTimeAsDouble() - currentSimulationTime.getTimeAsDouble());

            int index = getNewEventIndex();
            nextEventMap.put(index, event);
            timeSpanToNextEventMap.put(index, timeSpan);

            scheduleNextEvents();

        } catch (ScyllaRuntimeException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
            SimulationUtils.abort(model, processInstance, nodeId, traceIsOn());
        }
    }

    /**
     * Logging iniziale del tipo di task.
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
