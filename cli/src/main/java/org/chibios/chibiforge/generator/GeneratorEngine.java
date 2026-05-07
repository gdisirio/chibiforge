/*
    ChibiOS - Copyright (C) 2025-2026 Giovanni Di Sirio.

    This file is part of ChibiOS.

    ChibiOS is free software; you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation version 3 of the License.

    ChibiOS is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.chibios.chibiforge.generator;

import org.chibios.chibiforge.ChibiForgeException;
import org.chibios.chibiforge.component.ComponentDefinition;
import org.chibios.chibiforge.component.DependencyDef;
import org.chibios.chibiforge.config.ChibiForgeConfiguration;
import org.chibios.chibiforge.config.ComponentConfigEntry;
import org.chibios.chibiforge.config.ConfigLoader;
import org.chibios.chibiforge.config.TargetResolver;
import org.chibios.chibiforge.container.ComponentContainer;
import org.chibios.chibiforge.container.ComponentContent;
import org.chibios.chibiforge.datamodel.IdNormalizer;
import org.chibios.chibiforge.datamodel.DataModelBuilder;
import org.chibios.chibiforge.feature.FeatureChecker;
import org.chibios.chibiforge.registry.ComponentRegistry;
import org.chibios.chibiforge.resource.RefResolver;
import org.chibios.chibiforge.resource.ResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.*;

/**
 * Main generator pipeline orchestrator.
 * Wires all subsystems: config loading, component discovery, feature checking,
 * data model building, static payload copying, and template processing.
 */
public class GeneratorEngine {

    private static final Logger log = LoggerFactory.getLogger(GeneratorEngine.class);

    private final ConfigLoader configLoader = new ConfigLoader();
    private final TargetResolver targetResolver = new TargetResolver();
    private final FeatureChecker featureChecker = new FeatureChecker();
    private final ResourceLoader resourceLoader = new ResourceLoader();
    private final RefResolver refResolver = new RefResolver();
    private final DataModelBuilder dataModelBuilder = new DataModelBuilder();
    private final StaticPayloadCopier staticCopier = new StaticPayloadCopier();
    private final TemplateProcessor templateProcessor = new TemplateProcessor();
    private record ActiveComponent(ComponentConfigEntry entry, ComponentContainer container,
                                   ComponentDefinition definition, int originalOrder) {
    }

    public GenerationReport generate(GenerationContext ctx, Path componentsRoot) throws ChibiForgeException {
        return generate(ctx, componentsRoot, null);
    }

    public GenerationReport generate(GenerationContext ctx, List<Path> componentRoots)
            throws ChibiForgeException {
        GenerationReport report = new GenerationReport();

        // 1. Load configuration
        Path configPath = ctx.getConfigFile();
        log.info("Loading configuration: {}", configPath);
        ChibiForgeConfiguration config;
        try {
            config = configLoader.load(configPath);
        } catch (Exception e) {
            throw new ChibiForgeException(
                    "Failed to load configuration file '" + configPath + "': " + e.getMessage(), e);
        }

        // 2. Resolve target
        String target;
        try {
            target = targetResolver.resolve(config, ctx.getTarget());
        } catch (IllegalArgumentException e) {
            throw new ChibiForgeException(e.getMessage(), e);
        }
        log.info("Active target: {}", target);

        if (componentRoots != null) {
            for (Path root : componentRoots) {
                log.info("Scanning component root: {}", root);
            }
        }
        ComponentRegistry registry;
        try {
            registry = ComponentRegistry.build(componentRoots);
        } catch (Exception e) {
            throw new ChibiForgeException(
                    "Failed to scan component sources: " + e.getMessage(), e);
        }

        return generateWithRegistry(ctx, config, target, registry, report);
    }

    public GenerationReport generate(GenerationContext ctx, Path componentsRoot, Path pluginsRoot)
            throws ChibiForgeException {
        GenerationReport report = new GenerationReport();

        // 1. Load configuration
        Path configPath = ctx.getConfigFile();
        log.info("Loading configuration: {}", configPath);
        ChibiForgeConfiguration config;
        try {
            config = configLoader.load(configPath);
        } catch (Exception e) {
            throw new ChibiForgeException(
                    "Failed to load configuration file '" + configPath + "': " + e.getMessage(), e);
        }

        // 2. Resolve target
        String target;
        try {
            target = targetResolver.resolve(config, ctx.getTarget());
        } catch (IllegalArgumentException e) {
            throw new ChibiForgeException(e.getMessage(), e);
        }
        log.info("Active target: {}", target);

        // 3. Build component registry
        if (componentsRoot != null) {
            log.info("Scanning filesystem components: {}", componentsRoot);
        }
        if (pluginsRoot != null) {
            log.info("Scanning plugin JARs: {}", pluginsRoot);
        }
        ComponentRegistry registry;
        try {
            registry = ComponentRegistry.build(componentsRoot, pluginsRoot);
        } catch (Exception e) {
            throw new ChibiForgeException(
                    "Failed to scan component sources: " + e.getMessage(), e);
        }

        return generateWithRegistry(ctx, config, target, registry, report);
    }

    private GenerationReport generateWithRegistry(GenerationContext ctx,
                                                  ChibiForgeConfiguration config,
                                                  String target,
                                                  ComponentRegistry registry,
                                                  GenerationReport report) throws ChibiForgeException {

        // 4. Load component definitions
        Map<String, ActiveComponent> activeComponents = new LinkedHashMap<>();
        List<ComponentDefinition> definitions = new ArrayList<>();
        List<String> dependencyWarnings = new ArrayList<>();

        int originalOrder = 0;
        for (ComponentConfigEntry entry : config.getComponents()) {
            String compId = entry.getComponentId();
            String compVersion = entry.getComponentVersion();
            ComponentContainer container;
            try {
                container = registry.lookup(compId, compVersion);
            } catch (NoSuchElementException e) {
                throw new ChibiForgeException(
                        "Component '" + compId + "' version '" + compVersion +
                        "' referenced in configuration but not found. " +
                        "Check component sources and verify the exact component version is installed.", e);
            }
            ComponentDefinition def;
            try {
                def = container.loadDefinition();
            } catch (Exception e) {
                throw new ChibiForgeException(
                        "Failed to load schema.xml for component '" + compId + "': " + e.getMessage(), e);
            }
            activeComponents.put(compId, new ActiveComponent(entry, container, def, originalOrder++));
            definitions.add(def);
            log.info("Loaded component: {} ({})", def.getName(), compId);
        }

        List<ActiveComponent> orderedComponents = orderActiveComponents(activeComponents, dependencyWarnings);
        for (String warning : dependencyWarnings) {
            log.warn(warning);
            report.addWarning(warning);
        }

        // 5. Check feature dependencies
        List<String> warnings = featureChecker.check(definitions);
        for (String warning : warnings) {
            log.warn(warning);
            report.addWarning(warning);
        }

        // 6. Build all configs map for cross-component access
        Map<String, ComponentConfigEntry> allConfigs = new LinkedHashMap<>();
        for (ActiveComponent active : orderedComponents) {
            allConfigs.put(active.entry().getComponentId(), active.entry());
        }

        Map<String, String> allComponentPaths = new LinkedHashMap<>();
        for (ActiveComponent active : orderedComponents) {
            allComponentPaths.put(active.entry().getComponentId(),
                    "generated/" + IdNormalizer.normalize(active.entry().getComponentId()) + "/");
        }

        // 7. Process each component
        for (ActiveComponent active : orderedComponents) {
            ComponentConfigEntry entry = active.entry();
            String compId = entry.getComponentId();
            ComponentContainer container = active.container();
            ComponentContent content = container.getComponentContent();
            ComponentDefinition def = active.definition();

            log.info("Processing component: {}", compId);

            // Load resources
            Map<String, Object> resources;
            try {
                resources = resourceLoader.loadResources(def, content);
            } catch (Exception e) {
                throw new ChibiForgeException(
                        "Failed to load resources for component '" + compId + "': " + e.getMessage(), e);
            }

            // Resolve @ref: defaults and fill in missing properties
            refResolver.applyDefaults(def, entry.getConfigElement(), resources);

            // Build data model
            Map<String, Object> dataModel;
            try {
                dataModel = dataModelBuilder.buildDataModel(
                        compId, entry, allConfigs,
                        allComponentPaths.get(compId), allComponentPaths,
                        resources, ctx.getConfigRoot(), target);
            } catch (Exception e) {
                throw new ChibiForgeException(
                        "Failed to build data model for component '" + compId + "': " + e.getMessage(), e);
            }

            // Copy static payload
            try {
                staticCopier.copyPayloads(compId, content, ctx, report);
            } catch (Exception e) {
                throw new ChibiForgeException(
                        "Failed to copy static files for component '" + compId + "': " + e.getMessage(), e);
            }

            // Process templates
            try {
                templateProcessor.processTemplates(compId, content, dataModel, ctx, report);
            } catch (Exception e) {
                throw new ChibiForgeException(
                        "Failed to process templates for component '" + compId + "': " + e.getMessage(), e);
            }
        }

        return report;
    }

    private List<ActiveComponent> orderActiveComponents(Map<String, ActiveComponent> activeComponents,
                                                        List<String> warnings) throws ChibiForgeException {
        Map<String, Set<String>> outgoing = new LinkedHashMap<>();
        Map<String, Integer> indegree = new LinkedHashMap<>();
        for (String componentId : activeComponents.keySet()) {
            outgoing.put(componentId, new LinkedHashSet<>());
            indegree.put(componentId, 0);
        }

        for (ActiveComponent active : activeComponents.values()) {
            for (DependencyDef dependency : active.definition().getDepends()) {
                ActiveComponent dependencyTarget = activeComponents.get(dependency.getId());
                if (dependencyTarget == null) {
                    throw new ChibiForgeException("Component '" + active.entry().getComponentId()
                            + "' requires component '" + dependency.getId()
                            + "', but it is not present in the configuration.");
                }

                validateDependencyConstraint(active, dependencyTarget, dependency, warnings);
                if (outgoing.get(dependency.getId()).add(active.entry().getComponentId())) {
                    indegree.put(active.entry().getComponentId(),
                            indegree.get(active.entry().getComponentId()) + 1);
                }
            }
        }

        PriorityQueue<ActiveComponent> ready = new PriorityQueue<>(Comparator.comparingInt(ActiveComponent::originalOrder));
        for (ActiveComponent active : activeComponents.values()) {
            if (indegree.get(active.entry().getComponentId()) == 0) {
                ready.add(active);
            }
        }

        List<ActiveComponent> ordered = new ArrayList<>();
        while (!ready.isEmpty()) {
            ActiveComponent current = ready.poll();
            ordered.add(current);
            for (String dependentId : outgoing.get(current.entry().getComponentId())) {
                int remaining = indegree.get(dependentId) - 1;
                indegree.put(dependentId, remaining);
                if (remaining == 0) {
                    ready.add(activeComponents.get(dependentId));
                }
            }
        }

        if (ordered.size() != activeComponents.size()) {
            List<String> cycleComponents = indegree.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
            throw new ChibiForgeException("Hard dependency cycle detected among configured components: "
                    + String.join(", ", cycleComponents));
        }

        return ordered;
    }

    private void validateDependencyConstraint(ActiveComponent owner,
                                              ActiveComponent dependencyTarget,
                                              DependencyDef dependency,
                                              List<String> warnings) throws ChibiForgeException {
        if (dependency.hasExactVersion() && dependency.hasMinVersion()) {
            warnings.add("Component '" + owner.entry().getComponentId() + "' dependency '"
                    + dependency.getId() + "' declares both version and minVersion; using exact version '"
                    + dependency.getVersion() + "'.");
        }

        String resolvedVersion = dependencyTarget.entry().getComponentVersion();
        if (dependency.hasExactVersion() && !dependency.getVersion().equals(resolvedVersion)) {
            throw new ChibiForgeException("Component '" + owner.entry().getComponentId()
                    + "' requires component '" + dependency.getId() + "' version '"
                    + dependency.getVersion() + "', but the configuration selects version '"
                    + resolvedVersion + "'.");
        }
        if (!dependency.hasExactVersion() && dependency.hasMinVersion()
                && compareVersions(resolvedVersion, dependency.getMinVersion()) < 0) {
            throw new ChibiForgeException("Component '" + owner.entry().getComponentId()
                    + "' requires component '" + dependency.getId() + "' version >= '"
                    + dependency.getMinVersion() + "', but the configuration selects version '"
                    + resolvedVersion + "'.");
        }
    }

    private int compareVersions(String left, String right) throws ChibiForgeException {
        int[] leftParts = parseVersion(left);
        int[] rightParts = parseVersion(right);
        for (int i = 0; i < 3; i++) {
            int cmp = Integer.compare(leftParts[i], rightParts[i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    private int[] parseVersion(String version) throws ChibiForgeException {
        String[] parts = version.split("\\.");
        if (parts.length != 3) {
            throw new ChibiForgeException("Invalid semantic version: " + version);
        }
        try {
            return new int[] {
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
            };
        } catch (NumberFormatException e) {
            throw new ChibiForgeException("Invalid semantic version: " + version, e);
        }
    }
}
