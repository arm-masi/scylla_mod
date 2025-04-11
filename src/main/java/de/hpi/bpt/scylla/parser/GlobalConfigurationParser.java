package de.hpi.bpt.scylla.parser;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.jdom2.Element;
import org.jdom2.Namespace;

import de.hpi.bpt.scylla.SimulationManager;
import de.hpi.bpt.scylla.exception.ScyllaValidationException;
import de.hpi.bpt.scylla.logger.DebugLogger;
import de.hpi.bpt.scylla.model.global.GlobalConfiguration;
import de.hpi.bpt.scylla.model.global.resource.*;
import de.hpi.bpt.scylla.plugin_loader.PluginLoader;
import de.hpi.bpt.scylla.plugin_type.parser.EventOrderType;
import de.hpi.bpt.scylla.simulation.utils.DateTimeUtils;

public class GlobalConfigurationParser extends Parser<GlobalConfiguration> {

    public GlobalConfigurationParser(SimulationManager simulationEnvironment) {
        super(simulationEnvironment);
    }

    @Override
    public GlobalConfiguration parse(Element rootElement) throws ScyllaValidationException {
        System.out.println(rootElement.getNamespace());

        Iterator<EventOrderType> eventOrderTypesIterator = PluginLoader.dGetPlugins(EventOrderType.class);
        Map<String, EventOrderType> eventOrderTypes = new HashMap<>();
        while (eventOrderTypesIterator.hasNext()) {
            EventOrderType eot = eventOrderTypesIterator.next();
            eventOrderTypes.put(eot.getName(), eot);
        }

        Namespace bsimNamespace = rootElement.getNamespace();
        List<Element> globalConfigurationElements = rootElement.getChildren(null, bsimNamespace);

        String globalConfId = rootElement.getAttributeValue("id");
        Long randomSeed = null;
        ZoneId zoneId = ZoneId.of("UTC");
        Map<String, Resource> resources = new HashMap<>();
        List<EventOrderType> resourceAssignmentOrder = new ArrayList<>();

        Map<String, Map<String, String>> resourcesToTimetableIds = new HashMap<>();
        Map<String, List<TimetableItem>> timetables = new HashMap<>();

        for (Element el : globalConfigurationElements) {
            String elementName = el.getName();
            if (isKnownElement(elementName)) {
                if (el.getText().isEmpty()) {
                    continue;
                }
                if (elementName.equals("resourceAssignmentOrder")) {
                    String[] orderTypeArray = el.getText().split(",");
                    for (String orderTypeName : orderTypeArray) {
                        if (!orderTypeName.isEmpty()) {
                            EventOrderType eot = eventOrderTypes.get(orderTypeName);
                            if (eot == null) {
                                throw new ScyllaValidationException("Unknown event order type: " + orderTypeName);
                            }
                            resourceAssignmentOrder.add(eot);
                        }
                    }
                } else if (elementName.equals("randomSeed")) {
                    randomSeed = Long.parseLong(el.getText());
                } else if (elementName.equals("zoneOffset")) {
                    zoneId = ZoneId.of("GMT" + el.getText());
                } else if (elementName.equals("resourceData")) {
                    for (Element resourceElem : el.getChildren()) {
                        String resourceId = resourceElem.getAttributeValue("id");
                        String name = resourceElem.getAttributeValue("name");
                        int quantity = Integer.parseInt(resourceElem.getAttributeValue("defaultQuantity"));
                        double defaultCost = Double.parseDouble(resourceElem.getAttributeValue("defaultCost"));
                        TimeUnit timeUnit = TimeUnit.valueOf(resourceElem.getAttributeValue("defaultTimeUnit"));
                        String defaultTimetableId = resourceElem.getAttributeValue("defaultTimetableId");

                        DynamicResource resource = new DynamicResource(resourceId, name, quantity, defaultCost, timeUnit);
                        Map<String, DynamicResourceInstance> instances = resource.getResourceInstances();

                        if (!resourcesToTimetableIds.containsKey(resourceId)) {
                            resourcesToTimetableIds.put(resourceId, new HashMap<>());
                        }

                        List<Element> instanceElements = resourceElem.getChildren("instance", bsimNamespace);

                        int autoInstances = quantity - instanceElements.size();
                        for (int i = 0; i < autoInstances; i++) {
                            String instName = "#" + i;
                            DynamicResourceInstance instance = new DynamicResourceInstance(defaultCost, timeUnit);
                            instances.put(instName, instance);
                            if (defaultTimetableId != null) {
                                resourcesToTimetableIds.get(resourceId).put(instName, defaultTimetableId);
                            }
                        }

                        for (Element instEl : instanceElements) {
                            String instName = instEl.getAttributeValue("name");
                            double cost = instEl.getAttributeValue("cost") != null ? Double.parseDouble(instEl.getAttributeValue("cost")) : defaultCost;
                            TimeUnit unit = instEl.getAttributeValue("timeUnit") != null ? TimeUnit.valueOf(instEl.getAttributeValue("timeUnit")) : timeUnit;
                            DynamicResourceInstance instance = new DynamicResourceInstance(cost, unit);

                            // ✅ Popola le proprietà estese dalla configurazione
                            instEl.getAttributes().forEach(attr -> {
                                String key = attr.getName();
                                String value = attr.getValue();
                                if (!List.of("name", "cost", "timeUnit", "timetableId").contains(key)) {
                                    instance.setProperty(key, value);
                                }
                            });

                            instances.put(instName, instance);

                            String ttId = instEl.getAttributeValue("timetableId");
                            if (ttId != null) {
                                resourcesToTimetableIds.get(resourceId).put(instName, ttId);
                            } else if (defaultTimetableId != null) {
                                resourcesToTimetableIds.get(resourceId).put(instName, defaultTimetableId);
                            }
                        }
                        resources.put(resourceId, resource);
                    }
                } else if (elementName.equals("timetables")) {
                    for (Element tElement : el.getChildren("timetable", bsimNamespace)) {
                        String tId = tElement.getAttributeValue("id");
                        List<TimetableItem> items = new ArrayList<>();
                        for (Element tItem : tElement.getChildren("timetableItem", bsimNamespace)) {
                            DayOfWeek from = DayOfWeek.valueOf(tItem.getAttributeValue("from"));
                            DayOfWeek to = DayOfWeek.valueOf(tItem.getAttributeValue("to"));
                            LocalTime begin = LocalTime.parse(tItem.getAttributeValue("beginTime"));
                            LocalTime end = LocalTime.parse(tItem.getAttributeValue("endTime"));
                            if (from.compareTo(to) > 0) {
                                items.add(new TimetableItem(from, DayOfWeek.SUNDAY, begin, LocalTime.MAX));
                                items.add(new TimetableItem(DayOfWeek.MONDAY, to, LocalTime.MIN, end));
                            } else {
                                items.add(new TimetableItem(from, to, begin, end));
                            }
                        }
                        timetables.put(tId, items);
                    }
                }
            } else {
                DebugLogger.log("Element " + el.getName() + " of global configuration is not supported.");
            }
        }

        for (String resId : resourcesToTimetableIds.keySet()) {
            for (Map.Entry<String, String> entry : resourcesToTimetableIds.get(resId).entrySet()) {
                String instName = entry.getKey();
                String ttId = entry.getValue();
                if (!timetables.containsKey(ttId)) {
                    DebugLogger.log("Timetable " + ttId + " not found.");
                    continue;
                }
                ((DynamicResource) resources.get(resId)).getResourceInstances().get(instName).setTimetable(timetables.get(ttId));
            }
        }

        if (resources.isEmpty()) {
            System.err.println("[Warning:] No resource data definitions in file.");
        }
        if (randomSeed == null) {
            randomSeed = new Random().nextLong();
        }

        DebugLogger.log("Random seed for whole simulation: " + randomSeed);

        return new GlobalConfiguration(globalConfId, zoneId, randomSeed, resources, resourceAssignmentOrder);
    }

    private boolean isKnownElement(String name) {
        return List.of("resourceAssignmentOrder", "randomSeed", "zoneOffset", "resourceData", "timetables").contains(name);
    }
}
