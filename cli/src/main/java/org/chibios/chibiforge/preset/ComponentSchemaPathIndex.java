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

package org.chibios.chibiforge.preset;

import org.chibios.chibiforge.component.ComponentDefinition;
import org.chibios.chibiforge.component.LayoutDef;
import org.chibios.chibiforge.component.PropertyDef;
import org.chibios.chibiforge.component.SectionDef;
import org.chibios.chibiforge.datamodel.IdNormalizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Indexes component schema properties by normalized schema path.
 */
public final class ComponentSchemaPathIndex {

    /**
     * A property reachable by a normalized schema path.
     *
     * @param normalizedPath normalized path used for matching
     * @param originalSegments original section/property names in path order
     * @param property target property definition
     * @param ownerListPath nearest enclosing list-property path, or null for top-level matchable properties
     */
    public record SchemaPropertyPath(String normalizedPath, List<String> originalSegments,
                                     PropertyDef property, String ownerListPath) {
        public SchemaPropertyPath {
            Objects.requireNonNull(normalizedPath, "normalizedPath");
            Objects.requireNonNull(originalSegments, "originalSegments");
            Objects.requireNonNull(property, "property");
            originalSegments = List.copyOf(originalSegments);
        }

        public boolean isNestedUnderList() {
            return ownerListPath != null;
        }

        public String displayPath() {
            return String.join(" / ", originalSegments);
        }
    }

    private final String componentId;
    private final Map<String, SchemaPropertyPath> pathsByNormalizedPath;

    private ComponentSchemaPathIndex(String componentId, Map<String, SchemaPropertyPath> pathsByNormalizedPath) {
        this.componentId = componentId;
        this.pathsByNormalizedPath = Collections.unmodifiableMap(new LinkedHashMap<>(pathsByNormalizedPath));
    }

    public static ComponentSchemaPathIndex from(ComponentDefinition definition) {
        Objects.requireNonNull(definition, "definition");

        Map<String, SchemaPropertyPath> indexed = new LinkedHashMap<>();
        for (SectionDef section : definition.getSections()) {
            indexSection(definition.getId(), indexed, List.of(), section, null);
        }
        return new ComponentSchemaPathIndex(definition.getId(), indexed);
    }

    public String getComponentId() {
        return componentId;
    }

    public Map<String, SchemaPropertyPath> getPathsByNormalizedPath() {
        return pathsByNormalizedPath;
    }

    public List<SchemaPropertyPath> getAllPaths() {
        return List.copyOf(pathsByNormalizedPath.values());
    }

    public List<SchemaPropertyPath> getPresetMatchablePaths() {
        return pathsByNormalizedPath.values().stream()
                .filter(path -> !path.isNestedUnderList())
                .toList();
    }

    public Optional<SchemaPropertyPath> find(String normalizedPath) {
        return Optional.ofNullable(pathsByNormalizedPath.get(normalizedPath));
    }

    private static void indexSection(String componentId, Map<String, SchemaPropertyPath> indexed,
                                     List<String> parentSegments, SectionDef section, String ownerListPath) {
        List<String> sectionSegments = append(parentSegments, section.getName());
        indexChildren(componentId, indexed, sectionSegments, section.getChildren(), ownerListPath);
    }

    private static void indexChildren(String componentId, Map<String, SchemaPropertyPath> indexed,
                                      List<String> parentSegments, List<Object> children, String ownerListPath) {
        for (Object child : children) {
            if (child instanceof PropertyDef property) {
                indexProperty(componentId, indexed, parentSegments, property, ownerListPath);
            } else if (child instanceof LayoutDef layout) {
                indexChildren(componentId, indexed, parentSegments, layout.getChildren(), ownerListPath);
            }
        }
    }

    private static void indexProperty(String componentId, Map<String, SchemaPropertyPath> indexed,
                                      List<String> parentSegments, PropertyDef property, String ownerListPath) {
        List<String> propertySegments = append(parentSegments, property.getName());
        String normalizedPath = normalizePath(propertySegments);
        SchemaPropertyPath path = new SchemaPropertyPath(normalizedPath, propertySegments, property, ownerListPath);
        SchemaPropertyPath existing = indexed.putIfAbsent(normalizedPath, path);
        if (existing != null) {
            throw new IllegalArgumentException("Normalized schema path collision in component '"
                    + componentId + "': '" + existing.displayPath() + "' and '" + path.displayPath()
                    + "' both map to '" + normalizedPath + "'");
        }

        if (property.getType() == PropertyDef.Type.LIST) {
            for (SectionDef nestedSection : property.getNestedSections()) {
                indexSection(componentId, indexed, propertySegments, nestedSection, normalizedPath);
            }
        }
    }

    private static List<String> append(List<String> segments, String name) {
        List<String> result = new ArrayList<>(segments.size() + 1);
        result.addAll(segments);
        result.add(name);
        return result;
    }

    private static String normalizePath(List<String> segments) {
        return segments.stream()
                .map(IdNormalizer::normalize)
                .reduce((left, right) -> left + "/" + right)
                .orElseThrow(() -> new IllegalArgumentException("Schema path must contain at least one segment"));
    }
}
