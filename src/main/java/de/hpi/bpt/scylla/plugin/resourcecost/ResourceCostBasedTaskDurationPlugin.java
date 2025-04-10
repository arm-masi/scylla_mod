package de.hpi.bpt.scylla.plugin.resourcecost;

import de.hpi.bpt.scylla.plugin_type.simulation.event.TaskBeginEventPluggable;
import de.hpi.bpt.scylla.simulation.event.TaskBeginEvent;
import de.hpi.bpt.scylla.simulation.ProcessInstance;
import de.hpi.bpt.scylla.simulation.ResourceObject;
import de.hpi.bpt.scylla.simulation.ResourceObjectTuple;
import de.hpi.bpt.scylla.model.process.ProcessModel;

import java.util.Map;

public class ResourceCostBasedTaskDurationPlugin extends TaskBeginEventPluggable {

    @Override
    public String getName() {
        return "resourceCost";
    }

    @Override
    public void eventRoutine(TaskBeginEvent event, ProcessInstance processInstance) {
        try {
            int nodeId = event.getNodeId();
            String source = event.getSource();

            ProcessModel model = processInstance.getProcessModel();
            Map<String, String> attributes = model.getNodeAttributes().get(nodeId);

            double numInstructions = 1000.0;
            if (attributes != null && attributes.containsKey("numInstructions")) {
                numInstructions = Double.parseDouble(attributes.get("numInstructions"));
            }

            ResourceObjectTuple tuple = processInstance.getAssignedResources().get(source);
            double cpuSpeed = 1000.0;
            if (tuple != null && !tuple.getResourceObjects().isEmpty()) {
                ResourceObject res = tuple.getResourceObjects().iterator().next();
                cpuSpeed = res.getCost(); // usa "cost" come cpuSpeed
            }

            double duration = numInstructions / cpuSpeed;
            duration = Math.max(duration, 1.0); // minimo 1 secondo

            System.out.println("✅ [resourceCost] Plugin attivato per attività nodeId=" + nodeId);
            System.out.println("⏱ [resourceCost] Durata calcolata: " + duration + " sec (istruzioni: " + numInstructions + ", cpuSpeed: " + cpuSpeed + ")");

            // Isola l'impostazione della durata usando un metodo dedicato
            event.setCustomDuration(duration);

        } catch (Exception e) {
            System.err.println("❌ [resourceCost] Errore durante il calcolo della durata:");
            e.printStackTrace();
        }
    }
}
