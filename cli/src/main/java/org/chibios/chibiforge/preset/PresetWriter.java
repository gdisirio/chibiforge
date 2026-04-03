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
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Exports a component configuration as a preset document.
 */
public class PresetWriter {

    private static final String PRESET_NS = "http://www.example.org/chibiforge_preset/";

    public Document export(String presetName, ComponentDefinition definition,
                           Element componentElement, String activeTarget) throws Exception {
        if (presetName == null || presetName.isBlank()) {
            throw new IllegalArgumentException("Preset name must not be blank");
        }
        if (definition == null) {
            throw new IllegalArgumentException("Component definition must not be null");
        }
        if (componentElement == null) {
            throw new IllegalArgumentException("Component element must not be null");
        }

        String target = (activeTarget == null || activeTarget.isBlank()) ? "default" : activeTarget;
        Document document = createDocument();
        Element presetElement = document.createElementNS(PRESET_NS, "preset");
        presetElement.setAttribute("name", presetName);
        presetElement.setAttribute("id", definition.getId());
        presetElement.setAttribute("version", definition.getVersion());
        document.appendChild(presetElement);

        Element sectionsElement = document.createElementNS(PRESET_NS, "sections");
        presetElement.appendChild(sectionsElement);
        for (SectionDef section : definition.getSections()) {
            sectionsElement.appendChild(exportSection(document, section, componentElement, target));
        }
        return document;
    }

    public void save(String presetName, ComponentDefinition definition, Element componentElement,
                     String activeTarget, Path outputPath) throws Exception {
        Document document = export(presetName, definition, componentElement, activeTarget);

        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        Path parent = outputPath.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Writer writer = Files.newBufferedWriter(outputPath)) {
            transformer.transform(new DOMSource(document), new StreamResult(writer));
        }
    }

    private Element exportSection(Document document, SectionDef section, Element parentConfigElement,
                                  String activeTarget) {
        Element sectionElement = document.createElementNS(PRESET_NS, "section");
        sectionElement.setAttribute("name", section.getName());

        Element configSection = findDirectChild(parentConfigElement, IdNormalizer.normalize(section.getName()));
        appendPresetChildren(document, sectionElement, section.getChildren(), configSection, activeTarget);
        return sectionElement;
    }

    private void appendPresetChildren(Document document, Element presetSection, java.util.List<Object> children,
                                      Element configParent, String activeTarget) {
        for (Object child : children) {
            if (child instanceof PropertyDef property) {
                presetSection.appendChild(exportProperty(document, property, configParent, activeTarget));
            } else if (child instanceof LayoutDef layout) {
                appendPresetChildren(document, presetSection, layout.getChildren(), configParent, activeTarget);
            }
        }
    }

    private Element exportProperty(Document document, PropertyDef property, Element configParent, String activeTarget) {
        Element propertyElement = document.createElementNS(PRESET_NS, "property");
        propertyElement.setAttribute("name", property.getName());
        propertyElement.setAttribute("type", property.getType().name().toLowerCase());

        Element configProperty = configParent != null ? findDirectChild(configParent, property.getName()) : null;
        if (property.getType() == PropertyDef.Type.LIST) {
            propertyElement.appendChild(exportItems(document, property, configProperty, activeTarget));
        } else {
            Element valueElement = document.createElementNS(PRESET_NS, "value");
            valueElement.setTextContent(resolveScalarValue(property, configProperty, activeTarget));
            propertyElement.appendChild(valueElement);
        }
        return propertyElement;
    }

    private Element exportItems(Document document, PropertyDef property, Element configProperty, String activeTarget) {
        Element itemsElement = document.createElementNS(PRESET_NS, "items");
        if (configProperty == null) {
            return itemsElement;
        }
        if (isMultiTargetProperty(configProperty)) {
            throw new IllegalArgumentException("Preset export does not support multi-target list property '"
                    + property.getName() + "'");
        }

        NodeList children = configProperty.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element itemElement) {
                itemsElement.appendChild(exportItem(document, property, itemElement, activeTarget));
            }
        }
        return itemsElement;
    }

    private Element exportItem(Document document, PropertyDef property, Element configItem, String activeTarget) {
        Element itemElement = document.createElementNS(PRESET_NS, "item");
        Element sectionsElement = document.createElementNS(PRESET_NS, "sections");
        itemElement.appendChild(sectionsElement);

        for (SectionDef nestedSection : property.getNestedSections()) {
            sectionsElement.appendChild(exportSection(document, nestedSection, configItem, activeTarget));
        }
        return itemElement;
    }

    private String resolveScalarValue(PropertyDef property, Element configProperty, String activeTarget) {
        if (configProperty == null) {
            return property.getDefaultValue() != null ? property.getDefaultValue() : "";
        }

        if (!configProperty.hasAttribute("default")) {
            return configProperty.getTextContent().trim();
        }

        if (!"default".equals(activeTarget)) {
            NodeList children = configProperty.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element child
                        && matchesName(child, "targetValue")
                        && activeTarget.equals(child.getAttribute("target"))) {
                    return child.getTextContent().trim();
                }
            }
        }

        return configProperty.getAttribute("default");
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

    private Element findDirectChild(Element parent, String name) {
        if (parent == null) {
            return null;
        }
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child && matchesName(child, name)) {
                return child;
            }
        }
        return null;
    }

    private boolean matchesName(Element element, String name) {
        return name.equals(element.getTagName()) || name.equals(element.getLocalName());
    }

    private Document createDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.newDocument();
    }
}
