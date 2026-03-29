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
import org.chibios.chibiforge.config.ChibiForgeConfiguration;
import org.chibios.chibiforge.config.ComponentConfigEntry;
import org.chibios.chibiforge.config.ConfigLoader;
import org.chibios.chibiforge.config.TargetResolver;
import org.chibios.chibiforge.container.ComponentContainer;
import org.chibios.chibiforge.container.ComponentContent;
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

    public GenerationReport generate(GenerationContext ctx, Path componentsRoot) throws ChibiForgeException {
        return generate(ctx, componentsRoot, null);
    }

    public GenerationReport generate(GenerationContext ctx, Path componentsRoot, Path pluginsRoot)
            throws ChibiForgeException {
        GenerationReport report = new GenerationReport();

        // 1. Load configuration
        Path configPath = ctx.getConfigRoot().resolve("chibiforge.xcfg");
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

        // 4. Load component definitions
        Map<String, ComponentContainer> activeContainers = new LinkedHashMap<>();
        List<ComponentDefinition> definitions = new ArrayList<>();

        for (ComponentConfigEntry entry : config.getComponents()) {
            String compId = entry.getComponentId();
            ComponentContainer container;
            try {
                container = registry.lookup(compId);
            } catch (NoSuchElementException e) {
                throw new ChibiForgeException(
                        "Component '" + compId + "' referenced in configuration but not found. " +
                        "Check --components/--plugins paths and verify the component is installed.", e);
            }
            ComponentDefinition def;
            try {
                def = container.loadDefinition();
            } catch (Exception e) {
                throw new ChibiForgeException(
                        "Failed to load schema.xml for component '" + compId + "': " + e.getMessage(), e);
            }
            activeContainers.put(compId, container);
            definitions.add(def);
            log.info("Loaded component: {} ({})", def.getName(), compId);
        }

        // 5. Check feature dependencies
        List<String> warnings = featureChecker.check(definitions);
        for (String warning : warnings) {
            log.warn(warning);
            report.addWarning(warning);
        }

        // 6. Build all configs map for cross-component access
        Map<String, ComponentConfigEntry> allConfigs = new LinkedHashMap<>();
        for (ComponentConfigEntry entry : config.getComponents()) {
            allConfigs.put(entry.getComponentId(), entry);
        }

        // 7. Process each component
        for (ComponentConfigEntry entry : config.getComponents()) {
            String compId = entry.getComponentId();
            ComponentContainer container = activeContainers.get(compId);
            ComponentContent content = container.getComponentContent();
            ComponentDefinition def;
            try {
                def = container.loadDefinition();
            } catch (Exception e) {
                throw new ChibiForgeException(
                        "Failed to load definition for component '" + compId + "': " + e.getMessage(), e);
            }

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
                        compId, entry, allConfigs, resources, ctx.getConfigRoot(), target);
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
}
