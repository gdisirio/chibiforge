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

    private record RegisteredComponent(ComponentContainer container, SemanticVersion version, int precedence) {
    }

    private final Map<String, RegisteredComponent> preferredContainers;
    private final Map<String, NavigableMap<SemanticVersion, RegisteredComponent>> containersByIdAndVersion;

    private ComponentRegistry(Map<String, RegisteredComponent> preferredContainers,
                              Map<String, NavigableMap<SemanticVersion, RegisteredComponent>> containersByIdAndVersion) {
        this.preferredContainers = Collections.unmodifiableMap(preferredContainers);
        LinkedHashMap<String, NavigableMap<SemanticVersion, RegisteredComponent>> frozen = new LinkedHashMap<>();
        for (Map.Entry<String, NavigableMap<SemanticVersion, RegisteredComponent>> entry : containersByIdAndVersion.entrySet()) {
            frozen.put(entry.getKey(), Collections.unmodifiableNavigableMap(new TreeMap<>(entry.getValue())));
        }
        this.containersByIdAndVersion = Collections.unmodifiableMap(frozen);
    }

    /**
     * Build a registry from filesystem components and/or plugin JARs.
     * @param componentsRoot filesystem components root (may be null)
     * @param pluginsRoot plugin JARs root (may be null)
     */
    public static ComponentRegistry build(Path componentsRoot, Path pluginsRoot) throws IOException {
        Map<String, RegisteredComponent> preferred = new LinkedHashMap<>();
        Map<String, NavigableMap<SemanticVersion, RegisteredComponent>> exact = new LinkedHashMap<>();

        // Load JAR containers first (filesystem overrides JAR for same ID)
        if (pluginsRoot != null) {
            scanPlugins(pluginsRoot, preferred, exact, 0);
        }

        // Load filesystem containers (override JARs)
        if (componentsRoot != null) {
            scanFilesystem(componentsRoot, preferred, exact, 1);
        }

        log.info("Component registry: {} component(s) discovered", preferred.size());
        return new ComponentRegistry(preferred, exact);
    }

    /**
     * Build a registry from ordered component roots.
     * Roots are scanned from lowest to highest precedence so earlier list entries
     * override later ones.
     */
    public static ComponentRegistry build(List<Path> componentRoots) throws IOException {
        Map<String, RegisteredComponent> preferred = new LinkedHashMap<>();
        Map<String, NavigableMap<SemanticVersion, RegisteredComponent>> exact = new LinkedHashMap<>();

        if (componentRoots != null) {
            int precedence = 0;
            ListIterator<Path> it = componentRoots.listIterator(componentRoots.size());
            while (it.hasPrevious()) {
                scanRoot(it.previous(), preferred, exact, precedence++);
            }
        }

        log.info("Component registry: {} component(s) discovered", preferred.size());
        return new ComponentRegistry(preferred, exact);
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

    private static void scanRoot(Path root, Map<String, RegisteredComponent> preferred,
                                 Map<String, NavigableMap<SemanticVersion, RegisteredComponent>> exact,
                                 int precedence) throws IOException {
        if (root == null) {
            return;
        }
        if (Files.isRegularFile(root) && root.getFileName().toString().endsWith(".jar")) {
            scanPluginJar(root, preferred, exact, precedence);
            return;
        }
        if (!Files.isDirectory(root)) {
            return;
        }
        if (Files.exists(root.resolve("component").resolve("schema.xml"))) {
            registerContainer(new FilesystemContainer(root), preferred, exact, precedence);
            return;
        }
        scanPlugins(root, preferred, exact, precedence);
        scanFilesystem(root, preferred, exact, precedence);
    }

    private static void scanFilesystem(Path componentsRoot,
                                       Map<String, RegisteredComponent> preferred,
                                       Map<String, NavigableMap<SemanticVersion, RegisteredComponent>> exact,
                                       int precedence) throws IOException {
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

                registerContainer(new FilesystemContainer(dir), preferred, exact, precedence);
            }
        }
    }

    private static void scanPlugins(Path pluginsRoot,
                                    Map<String, RegisteredComponent> preferred,
                                    Map<String, NavigableMap<SemanticVersion, RegisteredComponent>> exact,
                                    int precedence) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(pluginsRoot, "*.jar")) {
            for (Path jarPath : stream) {
                scanPluginJar(jarPath, preferred, exact, precedence);
            }
        }
    }

    private static void scanPluginJar(Path jarPath,
                                      Map<String, RegisteredComponent> preferred,
                                      Map<String, NavigableMap<SemanticVersion, RegisteredComponent>> exact,
                                      int precedence) throws IOException {
        JarContainer container = JarContainer.openIfValid(jarPath);
        if (container == null) {
            log.debug("Skipping {}: not a ChibiForge plugin", jarPath.getFileName());
            return;
        }
        registerContainer(container, preferred, exact, precedence);
    }

    private static void registerContainer(ComponentContainer container,
                                          Map<String, RegisteredComponent> preferred,
                                          Map<String, NavigableMap<SemanticVersion, RegisteredComponent>> exact,
                                          int precedence) {
        try {
            String id = container.getId();
            SemanticVersion version = SemanticVersion.parse(container.loadDefinition().getVersion());
            RegisteredComponent registered = new RegisteredComponent(container, version, precedence);

            NavigableMap<SemanticVersion, RegisteredComponent> byVersion =
                    exact.computeIfAbsent(id, ignored -> new TreeMap<>());
            RegisteredComponent existing = byVersion.get(version);
            if (existing == null || precedence >= existing.precedence()) {
                if (existing != null) {
                    log.info("Component '{}' version '{}' overrides previous source", id, version.text());
                }
                byVersion.put(version, registered);
            }

            RegisteredComponent currentPreferred = preferred.get(id);
            if (currentPreferred == null
                    || precedence > currentPreferred.precedence()
                    || (precedence == currentPreferred.precedence()
                    && version.compareTo(currentPreferred.version()) > 0)) {
                if (currentPreferred != null && precedence > currentPreferred.precedence()) {
                    log.info("Higher-precedence component '{}' selected from preferred source", id);
                }
                preferred.put(id, registered);
            }

            log.debug("Discovered component '{}' version '{}'", id, version.text());
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException runtime) {
                throw runtime;
            }
            throw new IllegalArgumentException("Failed to register component container", e);
        }
    }

    /**
     * Look up a component container by ID.
     * @throws NoSuchElementException if not found
     */
    public ComponentContainer lookup(String componentId) {
        RegisteredComponent registered = preferredContainers.get(componentId);
        if (registered == null) {
            throw new NoSuchElementException(
                    "Component '" + componentId + "' not found in registry. " +
                    "Available: " + preferredContainers.keySet());
        }
        return registered.container();
    }

    /**
     * Look up a component container by exact ID and version.
     * @throws NoSuchElementException if not found
     */
    public ComponentContainer lookup(String componentId, String version) {
        NavigableMap<SemanticVersion, RegisteredComponent> versions = containersByIdAndVersion.get(componentId);
        if (versions == null) {
            throw new NoSuchElementException(
                    "Component '" + componentId + "' not found in registry. " +
                    "Available: " + preferredContainers.keySet());
        }

        SemanticVersion requested = SemanticVersion.parse(version);
        RegisteredComponent registered = versions.get(requested);
        if (registered == null) {
            throw new NoSuchElementException(
                    "Component '" + componentId + "' version '" + version + "' not found in registry. " +
                    "Available versions: " + availableVersions(componentId));
        }
        return registered.container();
    }

    public List<String> availableVersions(String componentId) {
        NavigableMap<SemanticVersion, RegisteredComponent> versions = containersByIdAndVersion.get(componentId);
        if (versions == null || versions.isEmpty()) {
            return List.of();
        }
        return versions.keySet().stream()
                .map(SemanticVersion::text)
                .toList();
    }

    public Optional<ComponentContainer> findLatestLaterVersion(String componentId, String version) {
        NavigableMap<SemanticVersion, RegisteredComponent> versions = containersByIdAndVersion.get(componentId);
        if (versions == null || versions.isEmpty()) {
            return Optional.empty();
        }
        SemanticVersion requested = SemanticVersion.parse(version);
        Map.Entry<SemanticVersion, RegisteredComponent> later = versions.higherEntry(requested);
        if (later == null) {
            return Optional.empty();
        }
        return Optional.of(versions.lastEntry().getValue().container());
    }

    public Collection<ComponentContainer> all() {
        return preferredContainers.values().stream()
                .map(RegisteredComponent::container)
                .toList();
    }

    public Set<String> componentIds() {
        return preferredContainers.keySet();
    }

    public int size() {
        return preferredContainers.size();
    }
}
