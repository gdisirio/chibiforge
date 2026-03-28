package org.chibios.chibiforge.registry;

import org.chibios.chibiforge.container.ComponentContainer;
import org.chibios.chibiforge.container.FilesystemContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Registry of discovered component containers.
 * Phase 1: filesystem containers only.
 */
public class ComponentRegistry {

    private static final Logger log = LoggerFactory.getLogger(ComponentRegistry.class);

    private final Map<String, ComponentContainer> containers;

    private ComponentRegistry(Map<String, ComponentContainer> containers) {
        this.containers = Collections.unmodifiableMap(containers);
    }

    /**
     * Build a registry by scanning a filesystem components root directory.
     * Each subdirectory that contains component/schema.xml is treated as a container.
     */
    public static ComponentRegistry fromFilesystem(Path componentsRoot) throws IOException {
        Map<String, ComponentContainer> map = new LinkedHashMap<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(componentsRoot)) {
            for (Path dir : stream) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                Path schemaPath = dir.resolve("component").resolve("schema.xml");
                if (!Files.exists(schemaPath)) {
                    log.debug("Skipping {}: no component/schema.xml", dir.getFileName());
                    continue;
                }

                FilesystemContainer container = new FilesystemContainer(dir);
                String id = container.getId();
                if (map.containsKey(id)) {
                    log.warn("Duplicate component ID '{}': {} overrides {}", id,
                            dir, ((FilesystemContainer) map.get(id)).getContainerRoot());
                }
                map.put(id, container);
                log.debug("Discovered component '{}' at {}", id, dir);
            }
        }

        log.info("Component registry: {} component(s) discovered", map.size());
        return new ComponentRegistry(map);
    }

    /**
     * Look up a component container by ID.
     * @throws NoSuchElementException if not found
     */
    public ComponentContainer lookup(String componentId) {
        ComponentContainer container = containers.get(componentId);
        if (container == null) {
            throw new NoSuchElementException(
                    "Component '" + componentId + "' not found in registry. " +
                    "Available: " + containers.keySet());
        }
        return container;
    }

    public Collection<ComponentContainer> all() {
        return containers.values();
    }

    public Set<String> componentIds() {
        return containers.keySet();
    }

    public int size() {
        return containers.size();
    }
}
