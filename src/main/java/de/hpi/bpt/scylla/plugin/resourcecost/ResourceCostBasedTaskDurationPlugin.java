package de.hpi.bpt.scylla.plugin.resourcecost;

import de.hpi.bpt.scylla.simulation.event.TaskBeginEvent;
import de.hpi.bpt.scylla.simulation.ProcessInstance;
import de.hpi.bpt.scylla.simulation.ResourceObjectTuple;
import de.hpi.bpt.scylla.simulation.ResourceObject;
import de.hpi.bpt.scylla.model.process.ProcessModel;

import java.util.Map;

public class ResourceCostBasedTaskDurationPlugin {

    public static void run(TaskBeginEvent event, ProcessInstance processInstance) {
        try {
            int nodeId = event.getNodeId();
            String source = event.getSource();

            ProcessModel model = processInstance.getProcessModel();

            // 1️⃣ Recupera le proprietà personalizzate dell'attività
            Map<String, String> attributes = model.getNodeAttributes().get(nodeId);
            double numInstructions = 1000.0;
            if (attributes != null && attributes.containsKey("numInstructions")) {
                numInstructions = Double.parseDouble(attributes.get("numInstructions"));
            }

            // 2️⃣ Recupera la risorsa assegnata
            ResourceObjectTuple tuple = processInstance.getAssignedResources().get(source);

            double cpuSpeed = 1000.0; // fallback default
            if (tuple != null && !tuple.getResourceObjects().isEmpty()) {
                ResourceObject res = tuple.getResourceObjects().iterator().next();

                // Workaround: per ora leggiamo "cpuSpeed" come cost, se lo hai salvato lì
                cpuSpeed = res.getCost(); // oppure hardcoded per test
            }

            // 3️⃣ Calcolo durata simulata
            double duration = numInstructions / cpuSpeed;
            duration = Math.max(duration, 1.0);

            // 4️⃣ Output in console
            System.out.println("⏱ Durata calcolata: " + duration + " [istruzioni: " + numInstructions +
                               ", cpuSpeed: " + cpuSpeed + "] per attività nodeId=" + nodeId);

        } catch (Exception e) {
            System.err.println("Errore nel plugin ResourceCostBasedTaskDurationPlugin:");
            e.printStackTrace();
        }
    }
}
