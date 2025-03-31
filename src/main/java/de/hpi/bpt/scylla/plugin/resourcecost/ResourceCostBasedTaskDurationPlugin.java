package de.hpi.bpt.scylla.plugin.resourcecost;

import de.hpi.bpt.scylla.plugin_type.simulation.event.TaskBeginEventPluggable;
import de.hpi.bpt.scylla.simulation.event.TaskBeginEvent;
import de.hpi.bpt.scylla.simulation.ProcessInstance;
import de.hpi.bpt.scylla.simulation.ResourceObjectTuple;
import de.hpi.bpt.scylla.simulation.ResourceObject;
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

            // 1️⃣ Recupera le proprietà personalizzate dell’attività
            Map<String, String> attributes = model.getNodeAttributes().get(nodeId);
            double numInstructions = 1000.0;
            if (attributes != null && attributes.containsKey("numInstructions")) {
                numInstructions = Double.parseDouble(attributes.get("numInstructions"));
            }

            // 2️⃣ Recupera la risorsa assegnata
            ResourceObjectTuple tuple = processInstance.getAssignedResources().get(source);
            double cpuSpeed = 1000.0; // fallback

            if (tuple != null && !tuple.getResourceObjects().isEmpty()) {
                ResourceObject res = tuple.getResourceObjects().iterator().next();
                cpuSpeed = res.getCost(); // usiamo il campo cost per cpuSpeed
            }

            // 3️⃣ Calcola la durata simulata
            double duration = numInstructions / cpuSpeed;
            duration = Math.max(duration, 1.0);

            // 4️⃣ Output console
            System.out.println("⏱ Durata calcolata: " + duration + " [istruzioni: " + numInstructions +
                    ", cpuSpeed: " + cpuSpeed + "] per attività nodeId=" + nodeId);

            // (Opzionale) Se vuoi sovrascrivere la durata della simulazione:
            // event.getModel().getDesmojObjects().overrideDistributionSample(nodeId, duration);

        } catch (Exception e) {
            System.err.println("Errore nel plugin ResourceCostBasedTaskDurationPlugin:");
            e.printStackTrace();
        }
    }
}
