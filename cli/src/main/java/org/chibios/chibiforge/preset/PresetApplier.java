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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Applies preset values onto a component configuration DOM.
 */
public class PresetApplier {

    public record ApplyOptions(String activeTarget, boolean createTargetOverride) {
        public ApplyOptions {
            if (activeTarget == null || activeTarget.isBlank()) {
                activeTarget = "default";
            }
        }

        public static ApplyOptions defaults() {
            return new ApplyOptions("default", false);
        }
    }

    private record FlatPresetProperty(String normalizedPath, List<String> originalSegments, PresetProperty property) {
        String displayPath() {
            return String.join(" / ", originalSegments);
        }
    }

    public PresetApplyReport apply(PresetDefinition preset, ComponentDefinition definition, Element componentElement) {
        return apply(preset, definition, componentElement, ApplyOptions.defaults());
    }

    public PresetApplyReport apply(PresetDefinition preset, ComponentDefinition definition,
                                   Element componentElement, ApplyOptions options) {
        Objects.requireNonNull(preset, "preset");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(componentElement, "componentElement");
        Objects.requireNonNull(options, "options");

        String componentId = componentElement.getAttribute("id");
        if (!definition.getId().equals(componentId)) {
            throw new IllegalArgumentException("Component DOM id '" + componentId
                    + "' does not match schema id '" + definition.getId() + "'");
        }
        if (!preset.componentId().equals(definition.getId())) {
            throw new IllegalArgumentException("Preset id '" + preset.componentId()
                    + "' does not match component id '" + definition.getId() + "'");
        }

        ComponentSchemaPathIndex schemaIndex = ComponentSchemaPathIndex.from(definition);
        Map<String, FlatPresetProperty> presetPaths = indexPresetProperties(preset);

        int updated = 0;
        int ignored = 0;
        int unchanged = 0;
        List<String> warnings = new ArrayList<>();

        for (FlatPresetProperty presetProperty : presetPaths.values()) {
            ComponentSchemaPathIndex.SchemaPropertyPath schemaPath = schemaIndex.find(presetProperty.normalizedPath())
                    .orElse(null);
            if (schemaPath == null || schemaPath.isNestedUnderList()) {
                ignored++;
                warnings.add("[Preset] Ignored property '" + presetProperty.displayPath()
                        + "' — path not present in component schema");
                continue;
            }

            PropertyDef schemaProperty = schemaPath.property();
            if (schemaProperty.getType() != presetProperty.property().type()) {
                throw new IllegalArgumentException("Preset property '" + presetProperty.displayPath()
                        + "' type '" + presetProperty.property().type().name().toLowerCase()
                        + "' does not match schema type '" + schemaProperty.getType().name().toLowerCase() + "'");
            }

            Element propertyElement = findOrCreatePropertyElement(componentElement, schemaPath);
            if (schemaProperty.getType() == PropertyDef.Type.LIST) {
                applyListPropertyValue(schemaProperty, presetProperty.property(), propertyElement,
                        warnings, presetProperty.displayPath());
                updated++;
            } else {
                String value = presetProperty.property().value();
                validateScalarValue(schemaProperty, presetProperty.displayPath(), value);

                String currentValue = getCurrentValue(propertyElement, options.activeTarget());
                if (Objects.equals(currentValue, value)) {
                    unchanged++;
                    continue;
                }

                setValue(propertyElement, options.activeTarget(), value, options.createTargetOverride());
                updated++;
            }
        }

        return new PresetApplyReport(updated, ignored, unchanged, warnings);
    }

    private Map<String, FlatPresetProperty> indexPresetProperties(PresetDefinition preset) {
        Map<String, FlatPresetProperty> indexed = new LinkedHashMap<>();
        for (PresetSection section : preset.sections()) {
            indexPresetSection(indexed, List.of(), section);
        }
        return indexed;
    }

    private void indexPresetSection(Map<String, FlatPresetProperty> indexed, List<String> parentSegments,
                                    PresetSection section) {
        List<String> sectionSegments = append(parentSegments, section.name());
        for (PresetProperty property : section.properties()) {
            List<String> propertySegments = append(sectionSegments, property.name());
            String normalizedPath = normalizePath(propertySegments);
            FlatPresetProperty flatProperty = new FlatPresetProperty(normalizedPath, propertySegments, property);
            FlatPresetProperty existing = indexed.putIfAbsent(normalizedPath, flatProperty);
            if (existing != null) {
                throw new IllegalArgumentException("Preset contains duplicate normalized schema path '"
                        + normalizedPath + "' for '" + existing.displayPath() + "' and '"
                        + flatProperty.displayPath() + "'");
            }
        }
    }

    private Element findOrCreatePropertyElement(Element componentElement,
                                                ComponentSchemaPathIndex.SchemaPropertyPath schemaPath) {
        List<String> segments = schemaPath.originalSegments();
        Element parent = componentElement;
        for (int i = 0; i < segments.size() - 1; i++) {
            parent = findOrCreateDirectChild(parent, IdNormalizer.normalize(segments.get(i)));
        }
        return findOrCreateDirectChild(parent, schemaPath.property().getName());
    }

    private void applyListPropertyValue(PropertyDef schemaProperty, PresetProperty presetProperty,
                                        Element propertyElement, List<String> warnings, String displayPath) {
        if (isMultiTargetProperty(propertyElement)) {
            throw new IllegalArgumentException("Preset list property '" + displayPath
                    + "' cannot be applied to a list value using unsupported target-specific encoding");
        }

        clearChildren(propertyElement);
        Document document = propertyElement.getOwnerDocument();
        int itemIndex = 0;
        for (PresetItem presetItem : presetProperty.items()) {
            itemIndex++;
            Element itemElement = createDefaultListItem(document, schemaProperty);
            applyPresetItem(schemaProperty, presetItem, itemElement, warnings, displayPath, itemIndex);
            propertyElement.appendChild(itemElement);
        }
    }

    private void applyPresetItem(PropertyDef listProperty, PresetItem presetItem, Element itemElement,
                                 List<String> warnings, String listDisplayPath, int itemIndex) {
        Map<String, SectionDef> sectionsByName = mapSectionsByNormalizedName(listProperty.getNestedSections());
        for (PresetSection presetSection : presetItem.sections()) {
            SectionDef schemaSection = sectionsByName.get(IdNormalizer.normalize(presetSection.name()));
            if (schemaSection == null) {
                warnMissingPropertiesInSection(presetSection, listDisplayPath + " / item[" + itemIndex + "]",
                        warnings);
                continue;
            }

            Element sectionElement = findOrCreateDirectChild(itemElement, IdNormalizer.normalize(schemaSection.getName()));
            applyPresetSection(schemaSection, presetSection, sectionElement,
                    listDisplayPath + " / item[" + itemIndex + "] / " + presetSection.name(), warnings);
        }
    }

    private void applyPresetSection(SectionDef schemaSection, PresetSection presetSection, Element sectionElement,
                                    String sectionDisplayPath, List<String> warnings) {
        Map<String, PropertyDef> schemaProperties = collectPropertiesByNormalizedName(schemaSection.getChildren());
        for (PresetProperty presetProperty : presetSection.properties()) {
            String propertyDisplayPath = sectionDisplayPath + " / " + presetProperty.name();
            PropertyDef schemaProperty = schemaProperties.get(IdNormalizer.normalize(presetProperty.name()));
            if (schemaProperty == null) {
                warnings.add("[Preset] Ignored property '" + propertyDisplayPath
                        + "' — path not present in component schema");
                continue;
            }

            if (schemaProperty.getType() != presetProperty.type()) {
                throw new IllegalArgumentException("Preset property '" + propertyDisplayPath
                        + "' type '" + presetProperty.type().name().toLowerCase()
                        + "' does not match schema type '" + schemaProperty.getType().name().toLowerCase() + "'");
            }

            Element propertyElement = findOrCreateDirectChild(sectionElement, schemaProperty.getName());
            if (schemaProperty.getType() == PropertyDef.Type.LIST) {
                applyListPropertyValue(schemaProperty, presetProperty, propertyElement, warnings, propertyDisplayPath);
            } else {
                validateScalarValue(schemaProperty, propertyDisplayPath, presetProperty.value());
                propertyElement.setTextContent(presetProperty.value());
            }
        }
    }

    private void warnMissingPropertiesInSection(PresetSection presetSection, String parentDisplayPath,
                                                List<String> warnings) {
        for (PresetProperty property : presetSection.properties()) {
            warnings.add("[Preset] Ignored property '" + parentDisplayPath + " / "
                    + presetSection.name() + " / " + property.name()
                    + "' — path not present in component schema");
        }
    }

    private Map<String, SectionDef> mapSectionsByNormalizedName(List<SectionDef> sections) {
        Map<String, SectionDef> mapped = new LinkedHashMap<>();
        for (SectionDef section : sections) {
            String normalized = IdNormalizer.normalize(section.getName());
            SectionDef existing = mapped.putIfAbsent(normalized, section);
            if (existing != null) {
                throw new IllegalArgumentException("Normalized nested section collision for '"
                        + existing.getName() + "' and '" + section.getName() + "'");
            }
        }
        return mapped;
    }

    private Map<String, PropertyDef> collectPropertiesByNormalizedName(List<Object> children) {
        Map<String, PropertyDef> mapped = new LinkedHashMap<>();
        collectPropertiesByNormalizedName(children, mapped);
        return mapped;
    }

    private void collectPropertiesByNormalizedName(List<Object> children, Map<String, PropertyDef> mapped) {
        for (Object child : children) {
            if (child instanceof PropertyDef property) {
                String normalized = IdNormalizer.normalize(property.getName());
                PropertyDef existing = mapped.putIfAbsent(normalized, property);
                if (existing != null) {
                    throw new IllegalArgumentException("Normalized property collision for '"
                            + existing.getName() + "' and '" + property.getName() + "'");
                }
            } else if (child instanceof LayoutDef layout) {
                collectPropertiesByNormalizedName(layout.getChildren(), mapped);
            }
        }
    }

    private Element createDefaultListItem(Document document, PropertyDef listProperty) {
        Element itemElement = document.createElement("item");
        for (SectionDef section : listProperty.getNestedSections()) {
            itemElement.appendChild(createDefaultSection(document, section));
        }
        return itemElement;
    }

    private Element createDefaultSection(Document document, SectionDef section) {
        Element sectionElement = document.createElement(IdNormalizer.normalize(section.getName()));
        appendDefaultChildren(document, sectionElement, section.getChildren());
        return sectionElement;
    }

    private void appendDefaultChildren(Document document, Element parent, List<Object> children) {
        for (Object child : children) {
            if (child instanceof PropertyDef property) {
                Element propertyElement = document.createElement(property.getName());
                if (property.getType() != PropertyDef.Type.LIST) {
                    propertyElement.setTextContent(property.getDefaultValue() != null ? property.getDefaultValue() : "");
                }
                parent.appendChild(propertyElement);
            } else if (child instanceof LayoutDef layout) {
                appendDefaultChildren(document, parent, layout.getChildren());
            }
        }
    }

    private boolean isMultiTargetProperty(Element propertyElement) {
        if (propertyElement.hasAttribute("default")) {
            return true;
        }
        NodeList children = propertyElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child && matchesName(child, "targetValue")) {
                return true;
            }
        }
        return false;
    }

    private void clearChildren(Element element) {
        while (element.getFirstChild() != null) {
            element.removeChild(element.getFirstChild());
        }
    }

    private Element findOrCreateDirectChild(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child && matchesName(child, name)) {
                return child;
            }
        }

        Document document = parent.getOwnerDocument();
        Element child = parent.getNamespaceURI() != null
                ? document.createElementNS(parent.getNamespaceURI(), name)
                : document.createElement(name);
        parent.appendChild(child);
        return child;
    }

    private boolean matchesName(Element element, String name) {
        return name.equals(element.getTagName()) || name.equals(element.getLocalName());
    }

    private String getCurrentValue(Element propertyElement, String target) {
        if (!propertyElement.hasAttribute("default")) {
            return propertyElement.getTextContent().trim();
        }

        if (!"default".equals(target)) {
            NodeList children = propertyElement.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element child
                        && matchesName(child, "targetValue")
                        && target.equals(child.getAttribute("target"))) {
                    return child.getTextContent().trim();
                }
            }
        }

        return propertyElement.getAttribute("default");
    }

    private void setValue(Element propertyElement, String target, String value, boolean createTargetOverride) {
        if (!propertyElement.hasAttribute("default")) {
            if (!createTargetOverride || "default".equals(target)) {
                propertyElement.setTextContent(value);
                return;
            }

            String defaultValue = propertyElement.getTextContent().trim();
            propertyElement.setTextContent("");
            propertyElement.setAttribute("default", defaultValue);
            appendTargetValue(propertyElement, target, value);
            return;
        }

        if ("default".equals(target)) {
            propertyElement.setAttribute("default", value);
            return;
        }

        Element explicitTarget = findTargetValueElement(propertyElement, target);
        if (explicitTarget != null) {
            explicitTarget.setTextContent(value);
            return;
        }

        if (createTargetOverride) {
            appendTargetValue(propertyElement, target, value);
            return;
        }

        propertyElement.setAttribute("default", value);
    }

    private Element findTargetValueElement(Element propertyElement, String target) {
        NodeList children = propertyElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child
                    && matchesName(child, "targetValue")
                    && target.equals(child.getAttribute("target"))) {
                return child;
            }
        }
        return null;
    }

    private void appendTargetValue(Element propertyElement, String target, String value) {
        Document document = propertyElement.getOwnerDocument();
        Element targetValue = propertyElement.getNamespaceURI() != null
                ? document.createElementNS(propertyElement.getNamespaceURI(), "targetValue")
                : document.createElement("targetValue");
        targetValue.setAttribute("target", target);
        targetValue.setTextContent(value);
        propertyElement.appendChild(targetValue);
    }

    private void validateScalarValue(PropertyDef property, String displayPath, String value) {
        switch (property.getType()) {
            case BOOL -> {
                if (!"true".equals(value) && !"false".equals(value)) {
                    throw new IllegalArgumentException("Preset property '" + displayPath
                            + "' value '" + value + "' is not a valid bool");
                }
            }
            case INT -> validateIntProperty(property, displayPath, value);
            case ENUM -> validateEnumProperty(property, displayPath, value);
            case STRING -> validateStringProperty(property, displayPath, value);
            case TEXT -> validateTextProperty(property, displayPath, value);
            case LIST -> throw new IllegalStateException("List properties must be handled separately");
        }
    }

    private void validateIntProperty(PropertyDef property, String displayPath, String value) {
        final int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Preset property '" + displayPath
                    + "' value '" + value + "' is not a valid int", e);
        }

        if (property.getIntMin() != null && parsed < Integer.parseInt(property.getIntMin())) {
            throw new IllegalArgumentException("Preset property '" + displayPath
                    + "' value '" + value + "' is below int_min '" + property.getIntMin() + "'");
        }
        if (property.getIntMax() != null && parsed > Integer.parseInt(property.getIntMax())) {
            throw new IllegalArgumentException("Preset property '" + displayPath
                    + "' value '" + value + "' is above int_max '" + property.getIntMax() + "'");
        }
    }

    private void validateEnumProperty(PropertyDef property, String displayPath, String value) {
        if (property.getEnumOf() == null || property.getEnumOf().isBlank()) {
            return;
        }

        for (String option : property.getEnumOf().split(",")) {
            if (option.trim().equals(value)) {
                return;
            }
        }

        throw new IllegalArgumentException("Preset property '" + displayPath
                + "' value '" + value + "' is not part of enum_of '" + property.getEnumOf() + "'");
    }

    private void validateStringProperty(PropertyDef property, String displayPath, String value) {
        if (property.getStringRegex() == null || property.getStringRegex().isBlank()) {
            return;
        }

        try {
            if (!Pattern.compile(property.getStringRegex()).matcher(value).matches()) {
                throw new IllegalArgumentException("Preset property '" + displayPath
                        + "' value '" + value + "' does not match string_regex '" + property.getStringRegex() + "'");
            }
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException("Schema property '" + displayPath
                    + "' has invalid string_regex '" + property.getStringRegex() + "'", e);
        }
    }

    private void validateTextProperty(PropertyDef property, String displayPath, String value) {
        if (property.getTextMaxsize() == null || property.getTextMaxsize().isBlank()) {
            return;
        }

        int maxSize = Integer.parseInt(property.getTextMaxsize());
        if (value.length() > maxSize) {
            throw new IllegalArgumentException("Preset property '" + displayPath
                    + "' length " + value.length() + " exceeds text_maxsize '" + maxSize + "'");
        }
    }

    private List<String> append(List<String> segments, String name) {
        List<String> result = new ArrayList<>(segments.size() + 1);
        result.addAll(segments);
        result.add(name);
        return result;
    }

    private String normalizePath(List<String> segments) {
        return segments.stream()
                .map(IdNormalizer::normalize)
                .reduce((left, right) -> left + "/" + right)
                .orElseThrow(() -> new IllegalArgumentException("Preset property path must contain at least one segment"));
    }
}
