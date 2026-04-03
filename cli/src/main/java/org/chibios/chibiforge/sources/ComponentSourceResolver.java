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

package org.chibios.chibiforge.sources;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Resolves component roots from project-local, sidecar, environment,
 * and manually supplied roots.
 */
public class ComponentSourceResolver {

    private static final String SIDECAR_FILE = "chibiforge_sources.json";
    private static final String COMPONENT_ROOTS_FIELD = "componentRoots";
    private static final String ENV_COMPONENTS = "CHIBIFORGE_COMPONENTS";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Function<String, String> envLookup;

    public ComponentSourceResolver() {
        this(System::getenv);
    }

    ComponentSourceResolver(Function<String, String> envLookup) {
        this.envLookup = Objects.requireNonNull(envLookup);
    }

    /**
     * Resolves component roots for a configuration file.
     * Returned roots are ordered from highest to lowest precedence.
     */
    public ResolvedComponentSources resolve(Path configFile, List<Path> preferredRoots) {
        Path configRoot = determineConfigRoot(configFile);
        List<String> warnings = new ArrayList<>();
        LinkedHashSet<Path> roots = new LinkedHashSet<>();

        addPreferredRoots(preferredRoots, roots, warnings);
        addSidecarRoots(configRoot, roots, warnings);
        addProjectLocalRoot(configRoot, roots);
        addEnvRoots(roots, warnings);

        if (roots.isEmpty()) {
            warnings.add("No valid component roots resolved for " + configRoot + ".");
        }

        return new ResolvedComponentSources(List.copyOf(roots), warnings);
    }

    private Path determineConfigRoot(Path configFile) {
        Path absolute = configFile.toAbsolutePath().normalize();
        Path parent = absolute.getParent();
        return parent != null ? parent : Path.of(".").toAbsolutePath().normalize();
    }

    private void addPreferredRoots(List<Path> preferredRoots,
                                   LinkedHashSet<Path> roots,
                                   List<String> warnings) {
        for (Path path : preferredRoots) {
            if (path == null) {
                continue;
            }
            addResolvedRoot(path.toAbsolutePath().normalize(), roots, warnings,
                    "Manual component root is invalid: ");
        }
    }

    private void addSidecarRoots(Path configRoot,
                                 LinkedHashSet<Path> roots,
                                 List<String> warnings) {
        Path sidecar = configRoot.resolve(SIDECAR_FILE);
        if (!Files.exists(sidecar)) {
            return;
        }

        try (InputStream input = Files.newInputStream(sidecar)) {
            JsonNode root = objectMapper.readTree(input);
            JsonNode componentRoots = root.path(COMPONENT_ROOTS_FIELD);
            if (!componentRoots.isArray()) {
                warnings.add("Invalid " + SIDECAR_FILE + ": expected array field '" +
                        COMPONENT_ROOTS_FIELD + "'.");
                return;
            }
            for (JsonNode item : componentRoots) {
                if (!item.isTextual()) {
                    warnings.add("Ignoring non-string component root in " + SIDECAR_FILE + ".");
                    continue;
                }
                Path resolved = configRoot.resolve(item.asText()).normalize().toAbsolutePath();
                addResolvedRoot(resolved, roots, warnings,
                        "Sidecar component root is invalid: ");
            }
        } catch (Exception e) {
            warnings.add("Failed to read " + SIDECAR_FILE + ": " + e.getMessage());
        }
    }

    private void addProjectLocalRoot(Path configRoot, LinkedHashSet<Path> roots) {
        Path localRoot = configRoot.resolve("components").normalize().toAbsolutePath();
        if (Files.isDirectory(localRoot)) {
            roots.add(localRoot);
        }
    }

    private void addEnvRoots(LinkedHashSet<Path> roots, List<String> warnings) {
        String value = envLookup.apply(ENV_COMPONENTS);
        if (value == null || value.isBlank()) {
            return;
        }

        String[] entries = value.split(java.util.regex.Pattern.quote(java.io.File.pathSeparator));
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            Path path = Path.of(entry).toAbsolutePath().normalize();
            addResolvedRoot(path, roots, warnings,
                    "Environment component root is invalid: ");
        }
    }

    private void addResolvedRoot(Path path,
                                 LinkedHashSet<Path> roots,
                                 List<String> warnings,
                                 String invalidPrefix) {
        if (Files.isDirectory(path) || Files.isRegularFile(path)) {
            roots.add(path);
        } else {
            warnings.add(invalidPrefix + path);
        }
    }
}
