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

package org.chibios.chibiforge.registry;

import org.chibios.chibiforge.container.ComponentContainer;
import org.chibios.chibiforge.container.FilesystemContainer;
import org.chibios.chibiforge.container.JarContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Registry of discovered component containers.
 * Supports filesystem directories and plugin JAR files.
 */
public class ComponentRegistry {

    private static final Logger log = LoggerFactory.getLogger(ComponentRegistry.class);

    private final Map<String, ComponentContainer> containers;

    private ComponentRegistry(Map<String, ComponentContainer> containers) {
        this.containers = Collections.unmodifiableMap(containers);
    }

    /**
     * Build a registry from filesystem components and/or plugin JARs.
     * @param componentsRoot filesystem components root (may be null)
     * @param pluginsRoot plugin JARs root (may be null)
     */
    public static ComponentRegistry build(Path componentsRoot, Path pluginsRoot) throws IOException {
        Map<String, ComponentContainer> map = new LinkedHashMap<>();

        // Load JAR containers first (filesystem overrides JAR for same ID)
        if (pluginsRoot != null) {
            scanPlugins(pluginsRoot, map);
        }

        // Load filesystem containers (override JARs)
        if (componentsRoot != null) {
            scanFilesystem(componentsRoot, map);
        }

        log.info("Component registry: {} component(s) discovered", map.size());
        return new ComponentRegistry(map);
    }

    /**
     * Build a registry from ordered component roots.
     * Roots are scanned from lowest to highest precedence so earlier list entries
     * override later ones.
     */
    public static ComponentRegistry build(List<Path> componentRoots) throws IOException {
        Map<String, ComponentContainer> map = new LinkedHashMap<>();

        if (componentRoots != null) {
            ListIterator<Path> it = componentRoots.listIterator(componentRoots.size());
            while (it.hasPrevious()) {
                scanRoot(it.previous(), map);
            }
        }

        log.info("Component registry: {} component(s) discovered", map.size());
        return new ComponentRegistry(map);
    }

    /**
     * Build a registry from filesystem components only.
     */
    public static ComponentRegistry fromFilesystem(Path componentsRoot) throws IOException {
        return build(componentsRoot, null);
    }

    /**
     * Build a registry from plugin JARs only.
     */
    public static ComponentRegistry fromPlugins(Path pluginsRoot) throws IOException {
        return build(null, pluginsRoot);
    }

    private static void scanRoot(Path root, Map<String, ComponentContainer> map) throws IOException {
        if (root == null) {
            return;
        }
        if (Files.isRegularFile(root) && root.getFileName().toString().endsWith(".jar")) {
            scanPluginJar(root, map);
            return;
        }
        if (!Files.isDirectory(root)) {
            return;
        }
        if (Files.exists(root.resolve("component").resolve("schema.xml"))) {
            registerFilesystemContainer(new FilesystemContainer(root), map);
            return;
        }
        scanPlugins(root, map);
        scanFilesystem(root, map);
    }

    private static void scanFilesystem(Path componentsRoot, Map<String, ComponentContainer> map) throws IOException {
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

                registerFilesystemContainer(new FilesystemContainer(dir), map);
            }
        }
    }

    private static void scanPlugins(Path pluginsRoot, Map<String, ComponentContainer> map) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsRoot, "*.jar")) {
            for (Path jarPath : stream) {
                scanPluginJar(jarPath, map);
            }
        }
    }

    private static void scanPluginJar(Path jarPath, Map<String, ComponentContainer> map) throws IOException {
        JarContainer container = JarContainer.openIfValid(jarPath);
        if (container == null) {
            log.debug("Skipping {}: not a ChibiForge plugin", jarPath.getFileName());
            return;
        }

        String id = container.getId();
        if (map.containsKey(id)) {
            log.warn("Duplicate plugin component ID '{}': {} overrides previous", id, jarPath);
        }
        map.put(id, container);
        log.debug("Discovered plugin component '{}' in {}", id, jarPath);
    }

    private static void registerFilesystemContainer(FilesystemContainer container,
                                                    Map<String, ComponentContainer> map) {
        String id = container.getId();
        if (map.containsKey(id)) {
            log.info("Filesystem component '{}' overrides JAR/previous source", id);
        }
        map.put(id, container);
        log.debug("Discovered filesystem component '{}' at {}", id, container.getContainerRoot());
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
