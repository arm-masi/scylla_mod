package de.hpi.bpt.scylla.plugin.resourcecost;

import de.hpi.bpt.scylla.plugin_type.simulation.event.TaskBeginEventPluggable;
import de.hpi.bpt.scylla.simulation.event.TaskBeginEvent;
import de.hpi.bpt.scylla.simulation.ProcessInstance;
import de.hpi.bpt.scylla.simulation.ResourceObject;
import de.hpi.bpt.scylla.simulation.ResourceObjectTuple;
import de.hpi.bpt.scylla.model.process.ProcessModel;
import de.hpi.bpt.scylla.utils.NumericUtils;

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

            double numInstructions = parseDouble(attributes, "numInstructions", 1000.0);
            double ramRequired = parseDouble(attributes, "ram", 0.0);
            double readOps = parseDouble(attributes, "readOps", 0.0);
            double writeOps = parseDouble(attributes, "writeOps", 0.0);
            double txMB = parseDouble(attributes, "txMB", 0.0);
            double rxMB = parseDouble(attributes, "rxMB", 0.0);

            ResourceObjectTuple tuple = processInstance.getAssignedResources().get(source);
            ResourceObject res = (tuple != null && !tuple.getResourceObjects().isEmpty())
                    ? tuple.getResourceObjects().iterator().next()
                    : null;

            double cpuSpeed = parseAttribute(res, "cpuSpeed", 1000.0);   // instr/sec
            double ramSpeed = parseAttribute(res, "ram", 1.0);         // MB/sec
            double ioReadSpeed = parseAttribute(res, "ioRead", 1.0);   // ops/sec
            double ioWriteSpeed = parseAttribute(res, "ioWrite", 1.0); // ops/sec
            double txSpeed = parseAttribute(res, "txSpeed", 1.0);       // MB/sec
            double rxSpeed = parseAttribute(res, "rxSpeed", 1.0);       // MB/sec

            // Usa NumericUtils per calcoli sicuri
            double timeCpu = NumericUtils.safeDivide(numInstructions, cpuSpeed);
            double timeRam = NumericUtils.safeDivide(ramRequired, ramSpeed);
            double timeIORead = NumericUtils.safeDivide(readOps, ioReadSpeed);
            double timeIOWrite = NumericUtils.safeDivide(writeOps, ioWriteSpeed);
            double timeTx = NumericUtils.safeDivide(txMB, txSpeed);
            double timeRx = NumericUtils.safeDivide(rxMB, rxSpeed);

            double totalTime = timeCpu + timeRam + timeIORead + timeIOWrite + timeTx + timeRx;
            totalTime = Math.max(totalTime, 1.0); // garantisce che non sia < 1 sec

            System.out.printf("\u2705 [resourceCost] nodeId=%d | CPU: %.2f | RAM: %.2f | IO: %.2f | NET: %.2f | TOTAL: %.2f sec%n",
                    nodeId, timeCpu, timeRam, timeIORead + timeIOWrite, timeTx + timeRx, totalTime);

            if (res != null) {
                System.out.printf("\uD83D\uDD0D [resourceCost] Resource %s: cpu=%.2f ram=%.2f ioR=%.2f ioW=%.2f tx=%.2f rx=%.2f%n",
                        res.getId(), cpuSpeed, ramSpeed, ioReadSpeed, ioWriteSpeed, txSpeed, rxSpeed);
            }
            System.out.println("[resourceCost] ➤ Esecuzione plugin per nodo " + nodeId);

            if (res == null) {
                System.err.println("❌ [resourceCost] Nessuna risorsa assegnata a " + source);
            } else {
                System.out.println("✅ [resourceCost] Risorsa assegnata: " + res.getId());
                System.out.println("CPU: " + cpuSpeed + " | RAM: " + ramSpeed + " | IOread: " + ioReadSpeed + " | IOWrite: " + ioWriteSpeed);
            }

            System.out.println("[resourceCost] ➤ Totale durata calcolata = " + totalTime);

            event.setCustomDuration(totalTime);

        } catch (Exception e) {
            System.err.println("\u274C [resourceCost] Errore nel calcolo durata:");
            e.printStackTrace();
        }
    }

    private double parseDouble(Map<String, String> attrs, String key, double defaultValue) {
        if (attrs != null && attrs.containsKey(key)) {
            try {
                return Double.parseDouble(attrs.get(key));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private double parseAttribute(ResourceObject res, String attrName, double defaultValue) {
        if (res == null) return defaultValue;

        try {
            // Per cpuSpeed, usiamo il metodo getCost() che sembra funzionare
            if (attrName.equals("cpuSpeed")) {
                try {
                    double cost = res.getCost();
                    return NumericUtils.getSafeValue(cost, defaultValue);
                } catch (Exception ignored) {
                    // Silenziosamente fallisce e usa il default
                }
            }
            
            // Proviamo a ottenere le proprietà usando il metodo getProperties()
            Map<String, String> properties = res.getProperties();
            if (properties != null && !properties.isEmpty()) {
                // Proviamo diverse varianti del nome dell'attributo
                String[] possibleKeys = {
                    attrName,
                    attrName.toLowerCase(),
                    "resource." + attrName,
                    "resource_" + attrName,
                    "capability." + attrName,
                    "performance." + attrName
                };
                
                for (String key : possibleKeys) {
                    if (properties.containsKey(key)) {
                        try {
                            double value = Double.parseDouble(properties.get(key));
                            System.out.println("[resourceCost] Trovato valore per " + attrName + " nelle proprietà: " + value);
                            return NumericUtils.getSafeValue(value, defaultValue);
                        } catch (NumberFormatException ignored) {
                            // Continuiamo se il parsing fallisce
                        }
                    }
                }
            }
            
            // Proviamo a cercare nelle capabilities della risorsa (se esiste il metodo)
            try {
                java.lang.reflect.Method getCapabilities = res.getClass().getMethod("getCapabilities");
                if (getCapabilities != null) {
                    Object capabilities = getCapabilities.invoke(res);
                    if (capabilities instanceof Map) {
                        Map<?, ?> capMap = (Map<?, ?>) capabilities;
                        if (capMap.containsKey(attrName)) {
                            Object value = capMap.get(attrName);
                            if (value instanceof Number) {
                                return NumericUtils.getSafeValue(((Number) value).doubleValue(), defaultValue);
                            } else if (value instanceof String) {
                                try {
                                    return NumericUtils.getSafeValue(Double.parseDouble(value.toString()), defaultValue);
                                } catch (NumberFormatException ignored) {
                                    // Continuiamo se il parsing fallisce
                                }
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
                // Se il metodo non esiste o fallisce, continuiamo
            }
            
            // Se non troviamo il valore nelle proprietà, usiamo valori hardcoded
            String resourceId = res.getId().toLowerCase();
            
            // Se la risorsa è di tipo server, impostiamo valori più alti
            if (resourceId.contains("server")) {
                if (attrName.equals("ram")) return 5.0;
                if (attrName.equals("ioRead")) return 5.0;
                if (attrName.equals("ioWrite")) return 5.0;
                if (attrName.equals("txSpeed")) return 5.0;
                if (attrName.equals("rxSpeed")) return 5.0;
            }
            
            // Valori standard per tutti gli altri tipi di risorse
            if (attrName.equals("ram")) return 1.0;
            if (attrName.equals("ioRead")) return 1.0;
            if (attrName.equals("ioWrite")) return 1.0;
            if (attrName.equals("txSpeed")) return 1.0;
            if (attrName.equals("rxSpeed")) return 1.0;
            
            // Se non è uno degli attributi conosciuti, usiamo il valore di default
            return defaultValue;
        } catch (Exception e) {
            // Log minimo in caso di errore
            System.err.println("[resourceCost] Errore nel recupero dell'attributo " + attrName + ": " + e.getMessage());
            return defaultValue;
        }
    }

    private void debugResourceProperties(ResourceObject res) {
        if (res == null) return;
        
        System.out.println("\n[resourceCost] DEBUG - Proprietà della risorsa " + res.getId() + ":");
        
        // Stampa le proprietà
        Map<String, String> properties = res.getProperties();
        if (properties != null && !properties.isEmpty()) {
            System.out.println("[resourceCost] Properties: " + properties);
        } else {
            System.out.println("[resourceCost] Nessuna proprietà trovata");
        }
        
        // Stampa tutti i metodi disponibili
        System.out.println("[resourceCost] Metodi disponibili:");
        for (java.lang.reflect.Method method : res.getClass().getMethods()) {
            if (method.getName().startsWith("get") && method.getParameterCount() == 0) {
                try {
                    Object result = method.invoke(res);
                    System.out.println("  - " + method.getName() + "() = " + result);
                } catch (Exception ignored) {
                    // Ignora errori nei metodi
                }
            }
        }
        System.out.println();
    }
}