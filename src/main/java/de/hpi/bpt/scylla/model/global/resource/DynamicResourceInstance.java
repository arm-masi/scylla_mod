package de.hpi.bpt.scylla.model.global.resource;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import de.hpi.bpt.scylla.simulation.utils.DateTimeUtils;

/**
 * Describes the instance of a dynamic resource.
 * Extended with properties for custom attributes.
 * 
 * @author Tsun Yin Wong
 */
public class DynamicResourceInstance {

    private List<TimetableItem> timetable = null;
    private double cost;
    private TimeUnit timeUnit;
    private Map<String, String> properties = new HashMap<>(); // ✅ Aggiunto

    /**
     * Constructor.
     * 
     * @param cost
     *            cost for using the resource instance in the given {@link #timeUnit}
     * @param timeUnit
     *            time unit for the {@link #cost}s
     */
    public DynamicResourceInstance(double cost, TimeUnit timeUnit) {
        this.cost = cost;
        this.timeUnit = timeUnit;
    }

    /**
     * null = any time
     * empty list = no time
     * list with timetable items = see items
     * 
     * @return list of time table items
     */
    public List<TimetableItem> getTimetable() {
        return timetable;
    }

    public void setTimetable(List<TimetableItem> timetable) {
        Comparator<TimetableItem> comparatorByWeekdayFromAndBeginTimeAsc = 
            DateTimeUtils.getComparatorByWeekdayFromAndBeginTimeAsc();
        Collections.sort(timetable, comparatorByWeekdayFromAndBeginTimeAsc);
        this.timetable = timetable;
    }

    public double getCost() {
        return cost;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    // ✅ Metodi aggiuntivi per le proprietà personalizzate
    public void setProperty(String key, String value) {
        properties.put(key, value);
    }

    public String getProperty(String key) {
        return properties.getOrDefault(key, "0");
    }

    public Map<String, String> getProperties() {
        return properties;
    }
}