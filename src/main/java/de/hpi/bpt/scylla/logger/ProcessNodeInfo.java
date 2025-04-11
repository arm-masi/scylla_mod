package de.hpi.bpt.scylla.logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 
 * Container class for logging business process-specific occurences.
 * 
 * @author Tsun Yin Wong
 *
 */
public class ProcessNodeInfo {

    private Integer id;
    private String processScopeNodeId;
    private String source;
    private long timestamp;
    private String taskName;
    private Set<String> resources;
    private ProcessNodeTransitionType transition;
    private Map<String, Object> dataObjectField; //holds for each processed Node (and so each field) the generated values of one specific instance

    /**
     * Constructor.
     * 
     * @param processScopeNodeId
     *            process node identifier which is unique on all levels of a process
     * @param source
     *            the DesmoJ event which logs the business process-specific occurence
     * @param timestamp
     *            time relative to simulation start
     * @param nodeName
     *            display name of the node
     * @param resources
     *            names of resource instances involved
     * @param transition
     *            transition of node
     */
    public ProcessNodeInfo(Integer id, String processScopeNodeId, String source, long timestamp, String nodeName,
            Set<String> resources, ProcessNodeTransitionType transition) {
        this.processScopeNodeId = processScopeNodeId;
        this.source = source;
        this.timestamp = timestamp;
        this.taskName = nodeName;
        this.resources = resources;
        this.transition = transition;
        this.dataObjectField = new HashMap<String, Object>();
        this.id = id;
    }
    
    /**
     * Verifica se il timestamp è valido
     * @return true se il timestamp è valido, false altrimenti
     */
    public boolean hasValidTimestamp() {
        return timestamp >= 0;
    }
    
    /**
     * Calcola la durata tra questo nodo e un altro nodo in modo sicuro
     * @param other il nodo con cui calcolare la durata
     * @return la durata calcolata o 0 se non è possibile calcolarla
     */
    public long calculateDurationSafely(ProcessNodeInfo other) {
        if (other == null || !hasValidTimestamp() || !other.hasValidTimestamp()) {
            return 0; // Ritorna 0 invece di NaN in caso di timestamp non validi
        }
        return Math.abs(this.timestamp - other.timestamp);
    }

    public String getProcessScopeNodeId() {
        return processScopeNodeId;
    }

    public String getSource() {
        return source;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getTaskName() {
        return taskName;
    }

    public Set<String> getResources() {
        return resources;
    }

    public ProcessNodeTransitionType getTransition() {
        return transition;
    }
    
    public Map<String, Object> getDataObjectField() {
    	return dataObjectField;
    }
    
    public void SetDataObjectField(Map<String, Object> dataObjectField) {
    	for (Map.Entry<String, Object> entry : dataObjectField.entrySet()) {
    	    String key = entry.getKey();
    	    Object value = entry.getValue();
    	    this.dataObjectField.put(key, value);
    	}
    }

    public Integer getId(){
        return id;
    }
}
