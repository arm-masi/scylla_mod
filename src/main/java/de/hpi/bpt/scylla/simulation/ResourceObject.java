package de.hpi.bpt.scylla.simulation;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.Map;
import java.util.HashMap;

import de.hpi.bpt.scylla.model.global.resource.TimetableItem;
import de.hpi.bpt.scylla.simulation.utils.DateTimeUtils;

/**
 * Describes a resource instance with extended attributes.
 */
public class ResourceObject {

    private String resourceType;
    private String id;
    private double timeOfLastAccess = 0;

    // ✅ Attributi estesi
    private double cost = 0;      // CPU speed (instr/sec)
    private double ram = 0;       // RAM in MB
    private double ioRead = 0;    // Read ops/sec
    private double ioWrite = 0;   // Write ops/sec
    private double txSpeed = 0;   // Transmit MB/sec
    private double rxSpeed = 0;   // Receive MB/sec

    private TimeUnit timeUnit = TimeUnit.DAYS;
    private List<TimetableItem> timetable;

    private int priority = 1;

    // ✅ Proprietà personalizzate aggiuntive
    private Map<String, String> properties = new HashMap<>();

    public ResourceObject(String resourceType, String id) {
        this.resourceType = resourceType;
        this.id = id;
    }

    public ResourceObject(String resourceType, String id, double cost, TimeUnit timeUnit, List<TimetableItem> timetable) {
        this(resourceType, id);
        this.cost = cost;
        this.timeUnit = timeUnit;
        this.timetable = timetable;
    }

    // ✅ Metodo per settare tutti gli attributi estesi in un colpo
    public void setExtendedAttributes(double ram, double ioRead, double ioWrite, double txSpeed, double rxSpeed) {
        this.ram = ram;
        this.ioRead = ioRead;
        this.ioWrite = ioWrite;
        this.txSpeed = txSpeed;
        this.rxSpeed = rxSpeed;

        // Impostiamo anche le property stringhe corrispondenti
        this.setProperty("ram", String.valueOf(ram));
        this.setProperty("ioRead", String.valueOf(ioRead));
        this.setProperty("ioWrite", String.valueOf(ioWrite));
        this.setProperty("txSpeed", String.valueOf(txSpeed));
        this.setProperty("rxSpeed", String.valueOf(rxSpeed));
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getId() {
        return id;
    }

    public double getCost() {
        return cost;
    }

    public void setCost(double cost) {
        this.cost = cost;
    }

    public double getRam() {
        return ram;
    }

    public void setRam(double ram) {
        this.ram = ram;
    }

    public double getIoRead() {
        return ioRead;
    }

    public void setIoRead(double ioRead) {
        this.ioRead = ioRead;
    }

    public double getIoWrite() {
        return ioWrite;
    }

    public void setIoWrite(double ioWrite) {
        this.ioWrite = ioWrite;
    }

    public double getTxSpeed() {
        return txSpeed;
    }

    public void setTxSpeed(double txSpeed) {
        this.txSpeed = txSpeed;
    }

    public double getRxSpeed() {
        return rxSpeed;
    }

    public void setRxSpeed(double rxSpeed) {
        this.rxSpeed = rxSpeed;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public List<TimetableItem> getTimetable() {
        return timetable;
    }

    public int getPriority() {
        return priority;
    }

    public double getTimeOfLastAccess() {
        return timeOfLastAccess;
    }

    public void setTimeOfLastAccess(double timeOfLastAccess) {
        this.timeOfLastAccess = timeOfLastAccess;
    }

    public boolean isAvailable(ZonedDateTime currentDateTime) {
        if (timetable == null) {
            return true;
        }
        for (TimetableItem item : timetable) {
            if (DateTimeUtils.isWithin(currentDateTime, item)) {
                return true;
            }
        }
        return false;
    }

    // ✅ Accesso e salvataggio proprietà custom
    public void setProperty(String key, String value) {
        properties.put(key, value);
    }

    public String getProperty(String key) {
        return properties.getOrDefault(key, "0");
    }

    public Map<String, String> getProperties() {
        return properties;
    }
    public void setTimetable(List<TimetableItem> timetable) {
    this.timetable = timetable;
}

}
