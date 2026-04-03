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

import org.chibios.chibiforge.component.PropertyDef;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a preset XML file validated against the preset XSD.
 */
public class PresetLoader {

    private static final String PRESET_SCHEMA_RESOURCE = "/schemas/chibiforge_preset.xsd";

    public PresetDefinition load(Path presetPath) throws Exception {
        try (InputStream is = Files.newInputStream(presetPath)) {
            return load(is);
        }
    }

    public PresetDefinition load(InputStream input) throws Exception {
        Document doc = parse(input);
        validate(doc);
        return load(doc);
    }

    public PresetDefinition load(Document doc) {
        Element root = doc.getDocumentElement();
        if (!"preset".equals(root.getLocalName())) {
            throw new IllegalArgumentException(
                    "Expected root element <preset>, got <" + root.getLocalName() + ">");
        }

        String name = root.getAttribute("name");
        String componentId = root.getAttribute("id");
        String version = root.getAttribute("version");

        Element sectionsElement = getRequiredDirectChild(root, "sections");
        List<PresetSection> sections = parseSections(sectionsElement);

        return new PresetDefinition(name, componentId, version, sections);
    }

    private Document parse(InputStream input) throws Exception {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(input);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid preset XML: " + e.getMessage(), e);
        }
    }

    private void validate(Document doc) throws Exception {
        try {
            loadSchema().newValidator().validate(new DOMSource(doc));
        } catch (Exception e) {
            throw new IllegalArgumentException("Preset does not conform to preset schema: " + e.getMessage(), e);
        }
    }

    private Schema loadSchema() throws Exception {
        URL schemaUrl = PresetLoader.class.getResource(PRESET_SCHEMA_RESOURCE);
        if (schemaUrl == null) {
            throw new IllegalStateException("Preset schema resource not found: " + PRESET_SCHEMA_RESOURCE);
        }
        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        return schemaFactory.newSchema(schemaUrl);
    }

    private List<PresetSection> parseSections(Element sectionsElement) {
        List<PresetSection> sections = new ArrayList<>();
        for (Element sectionElement : getDirectChildElements(sectionsElement, "section")) {
            sections.add(parseSection(sectionElement));
        }
        return sections;
    }

    private PresetSection parseSection(Element sectionElement) {
        String name = sectionElement.getAttribute("name");
        List<PresetProperty> properties = new ArrayList<>();
        for (Element propertyElement : getDirectChildElements(sectionElement, "property")) {
            properties.add(parseProperty(propertyElement));
        }
        return new PresetSection(name, properties);
    }

    private PresetProperty parseProperty(Element propertyElement) {
        String name = propertyElement.getAttribute("name");
        PropertyDef.Type type = PropertyDef.Type.fromString(propertyElement.getAttribute("type"));

        Element valueElement = getOptionalDirectChild(propertyElement, "value");
        Element sectionsElement = getOptionalDirectChild(propertyElement, "sections");

        if (type == PropertyDef.Type.LIST) {
            if (sectionsElement == null) {
                throw new IllegalArgumentException(
                        "Preset list property '" + name + "' must contain nested <sections>");
            }
            if (valueElement != null) {
                throw new IllegalArgumentException(
                        "Preset list property '" + name + "' must not contain <value>");
            }
            return new PresetProperty(name, type, null, parseSections(sectionsElement));
        }

        if (valueElement == null) {
            throw new IllegalArgumentException(
                    "Preset scalar property '" + name + "' must contain <value>");
        }
        if (sectionsElement != null) {
            throw new IllegalArgumentException(
                    "Preset scalar property '" + name + "' must not contain nested <sections>");
        }
        return new PresetProperty(name, type, valueElement.getTextContent(), List.of());
    }

    private Element getRequiredDirectChild(Element parent, String name) {
        Element child = getOptionalDirectChild(parent, name);
        if (child == null) {
            throw new IllegalArgumentException(
                    "Missing required child <" + name + "> in <" + parent.getLocalName() + ">");
        }
        return child;
    }

    private Element getOptionalDirectChild(Element parent, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(localName(element))) {
                return element;
            }
        }
        return null;
    }

    private List<Element> getDirectChildElements(Element parent, String name) {
        List<Element> elements = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && name.equals(localName(element))) {
                elements.add(element);
            }
        }
        return elements;
    }

    private String localName(Element element) {
        return element.getLocalName() != null ? element.getLocalName() : element.getTagName();
    }
}
