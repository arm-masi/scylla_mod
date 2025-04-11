package de.hpi.bpt.scylla.parser;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.jdom2.Element;
import org.jdom2.Namespace;

import de.hpi.bpt.scylla.SimulationManager;
import de.hpi.bpt.scylla.exception.ScyllaValidationException;
import de.hpi.bpt.scylla.logger.DebugLogger;
import de.hpi.bpt.scylla.model.NodeMap;
import de.hpi.bpt.scylla.model.configuration.ResourceReference;
import de.hpi.bpt.scylla.model.configuration.SimulationConfiguration;
import de.hpi.bpt.scylla.model.configuration.distribution.*;
import de.hpi.bpt.scylla.model.global.resource.Resource;
import de.hpi.bpt.scylla.model.process.ProcessModel;
import de.hpi.bpt.scylla.simulation.utils.DateTimeUtils;

public class SimulationConfigurationParser extends Parser<SimulationConfiguration> {

    public SimulationConfigurationParser(SimulationManager simulationEnvironment) {
        super(simulationEnvironment);
    }

    @Override
    public SimulationConfiguration parse(Element rootElement) throws ScyllaValidationException {
        Namespace simNamespace = rootElement.getNamespace();
        List<Element> simElements = rootElement.getChildren("simulationConfiguration", simNamespace);

        if (simElements.isEmpty()) {
            throw new ScyllaValidationException("No simulation configuration in file.");
        } else if (simElements.size() > 1) {
            throw new ScyllaValidationException("Multiple simulation configurations in file.");
        }

        Element sim = simElements.get(0);
        String processRef = sim.getAttributeValue("processRef");
        ProcessModel processModel = simulationEnvironment.getProcessModels().get(processRef);
        Long randomSeed = simulationEnvironment.getGlobalConfiguration().getRandomSeed();

        return parseSimulationConfiguration(sim, simNamespace, processRef, processModel, randomSeed);
    }

    private SimulationConfiguration parseSimulationConfiguration(Element sim, Namespace simNamespace,
                                                                  String processIdFromSimElement,
                                                                  ProcessModel processModel,
                                                                  Long randomSeed) throws ScyllaValidationException {

        Map<String, Resource> resources = simulationEnvironment.getGlobalConfiguration().getResources();
        if (processModel == null) {
            throw new ScyllaValidationException("Unknown process reference: " + processIdFromSimElement);
        }

        String simId = sim.getAttributeValue("id");
        Integer numberOfProcessInstances = Integer.parseInt(sim.getAttributeValue("processInstances"));
        ZonedDateTime startDateTime = DateTimeUtils.parse(sim.getAttributeValue("startDateTime"));
        ZonedDateTime endDateTime = null;
        if (sim.getAttributeValue("endDateTime") != null) {
            endDateTime = DateTimeUtils.parse(sim.getAttributeValue("endDateTime"));
        }

        if (sim.getAttributeValue("randomSeed") != null) {
            randomSeed = Long.valueOf(sim.getAttributeValue("randomSeed"));
        }

        Map<Integer, TimeDistributionWrapper> arrivalRates = new HashMap<>();
        Map<Integer, TimeDistributionWrapper> durations = new HashMap<>();
        Map<Integer, TimeDistributionWrapper> setUpDurations = new HashMap<>();
        Map<Integer, Set<ResourceReference>> resourceReferences = new NodeMap<>(processModel.getNumberOfNodes());
        Map<Integer, SimulationConfiguration> subConfigs = new HashMap<>();

        for (Element el : sim.getChildren()) {
            String name = el.getName();
            String id = el.getAttributeValue("id");
            Integer nodeId = processModel.getIdentifiersToNodeIds().get(id);

            if (name.equals("startEvent") && nodeId != null) {
                Element arr = el.getChild("arrivalRate", simNamespace);
                if (arr != null) arrivalRates.put(nodeId, getTimeDistributionWrapper(arr, simNamespace));

            } else if ((name.equals("task") || name.endsWith("Task") || name.equals("subProcess")) && nodeId != null) {
                Element dur = el.getChild("duration", simNamespace);
                if (dur != null) durations.put(nodeId, getTimeDistributionWrapper(dur, simNamespace));

                Element setup = el.getChild("setUpDuration", simNamespace);
                if (setup != null) setUpDurations.put(nodeId, getTimeDistributionWrapper(setup, simNamespace));

                Element resEl = el.getChild("resources", simNamespace);
                if (resEl != null) {
                    Set<ResourceReference> refs = new HashSet<>();
                    for (Element res : resEl.getChildren("resource", simNamespace)) {
                        String resId = res.getAttributeValue("id");
                        int amount = Integer.parseInt(res.getAttributeValue("amount"));
                        Map<String, String> assignDef = new HashMap<>();
                        Element def = res.getChild("assignmentDefinition", simNamespace);
                        if (def != null) {
                            for (Element e : def.getChildren()) {
                                assignDef.put(e.getName(), e.getText());
                            }
                        }
                        refs.add(new ResourceReference(resId, amount, assignDef));
                    }
                    resourceReferences.put(nodeId, refs);
                }

                if (name.equals("subProcess")) {
                    ProcessModel sub = processModel.getSubProcesses().get(nodeId);
                    subConfigs.put(nodeId, parseSimulationConfiguration(el, simNamespace, id, sub, randomSeed));
                }
            }
        }

        return new SimulationConfiguration(simId, processModel, numberOfProcessInstances, startDateTime,
                endDateTime, randomSeed, arrivalRates, durations, setUpDurations, resourceReferences, subConfigs);
    }

    public static TimeDistributionWrapper getTimeDistributionWrapper(Element element, Namespace ns) throws ScyllaValidationException {
        Distribution d = getDistribution(element, ns, "");
        TimeUnit u = TimeUnit.valueOf(element.getAttributeValue("timeUnit"));
        TimeDistributionWrapper w = new TimeDistributionWrapper(u);
        w.setDistribution(d);
        return w;
    }

    private static String getTaskOfDistribution(Element element) {
        return ((Element) element.getParent()).getAttributeValue("id");
    }

    public static Distribution getDistribution(Element element, Namespace ns, String fieldType) throws ScyllaValidationException {
        if (element.getChild("constantDistribution", ns) != null) {
            return new ConstantDistribution(Double.parseDouble(element.getChild("constantDistribution", ns).getChildText("constantValue", ns)));
        } else if (element.getChild("exponentialDistribution", ns) != null) {
            return new ExponentialDistribution(Double.parseDouble(element.getChild("exponentialDistribution", ns).getChildText("mean", ns)));
        } else if (element.getChild("uniformDistribution", ns) != null) {
            Element e = element.getChild("uniformDistribution", ns);
            return new UniformDistribution(Double.parseDouble(e.getChildText("lower", ns)), Double.parseDouble(e.getChildText("upper", ns)));
        } else if (element.getChild("normalDistribution", ns) != null) {
            Element e = element.getChild("normalDistribution", ns);
            return new NormalDistribution(Double.parseDouble(e.getChildText("mean", ns)), Double.parseDouble(e.getChildText("standardDeviation", ns)));
        } else if (element.getChild("triangularDistribution", ns) != null) {
            Element e = element.getChild("triangularDistribution", ns);
            return new TriangularDistribution(Double.parseDouble(e.getChildText("lower", ns)),
                    Double.parseDouble(e.getChildText("upper", ns)), Double.parseDouble(e.getChildText("peak", ns)));
        } else if (element.getChild("poissonDistribution", ns) != null) {
            return new PoissonDistribution(Double.parseDouble(element.getChild("poissonDistribution", ns).getChildText("mean", ns)));
        } else if (element.getChild("arbitraryFiniteProbabilityDistribution", ns) != null) {
            Element distEl = element.getChild("arbitraryFiniteProbabilityDistribution", ns);
            List<Element> entries = distEl.getChildren("entry", ns);
            double sum = 0;
            for (Element entry : entries) {
                sum += Double.parseDouble(entry.getAttributeValue("frequency"));
            }
            if (fieldType.equals("string")) {
                EmpiricalStringDistribution dist = new EmpiricalStringDistribution();
                for (Element entry : entries) {
                    dist.addEntry(entry.getAttributeValue("value"), Double.parseDouble(entry.getAttributeValue("frequency")) / sum);
                }
                return dist;
            } else {
                EmpiricalDistribution dist = new EmpiricalDistribution();
                for (Element entry : entries) {
                    dist.addEntry(Double.parseDouble(entry.getAttributeValue("value")), Double.parseDouble(entry.getAttributeValue("frequency")) / sum);
                }
                return dist;
            }
        } else {
            throw new ScyllaValidationException("Unknown distribution in: " + getTaskOfDistribution(element));
        }
    }
}