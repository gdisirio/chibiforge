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

package org.chibios.chibiforge.component;

import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a component/schema.xml file into a {@link ComponentDefinition}.
 */
public class ComponentDefinitionParser {

    private static final String NS = "http://chibiforge/schema/component";

    public ComponentDefinition parse(InputStream input) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(input);

        Element root = doc.getDocumentElement();
        if (!"component".equals(root.getLocalName())) {
            throw new IllegalArgumentException("Expected root element <component>, got <" + root.getLocalName() + ">");
        }

        String id = requireAttr(root, "id");
        String name = requireAttr(root, "name");
        String version = requireAttr(root, "version");
        boolean hidden = "true".equals(requireAttr(root, "hidden"));
        boolean isPlatform = "true".equals(requireAttr(root, "is_platform"));

        String description = null;
        List<ResourceDef> resources = new ArrayList<>();
        List<String> categories = new ArrayList<>();
        List<FeatureDef> requires = new ArrayList<>();
        List<DependencyDef> depends = new ArrayList<>();
        List<FeatureDef> provides = new ArrayList<>();
        List<SectionDef> sections = new ArrayList<>();

        for (Element child : childElements(root)) {
            switch (child.getLocalName()) {
                case "description" -> description = child.getTextContent().trim();
                case "resources" -> resources = parseResources(child);
                case "categories" -> categories = parseCategories(child);
                case "requires" -> requires = parseFeatures(child);
                case "depends" -> depends = parseDependencies(child);
                case "provides" -> provides = parseFeatures(child);
                case "sections" -> sections = parseSections(child);
                default -> throw new IllegalArgumentException(
                        "Unexpected element <" + child.getLocalName() + "> under <component>");
            }
        }

        return new ComponentDefinition(id, name, version, hidden, isPlatform, description,
                resources, categories, requires, depends, provides, sections);
    }

    private List<ResourceDef> parseResources(Element resourcesEl) {
        List<ResourceDef> result = new ArrayList<>();
        for (Element el : childElements(resourcesEl)) {
            if ("resource".equals(el.getLocalName())) {
                result.add(new ResourceDef(
                        requireAttr(el, "id"),
                        requireAttr(el, "file")
                ));
            }
        }
        return result;
    }

    private List<String> parseCategories(Element categoriesEl) {
        List<String> result = new ArrayList<>();
        for (Element el : childElements(categoriesEl)) {
            if ("category".equals(el.getLocalName())) {
                result.add(requireAttr(el, "id"));
            }
        }
        return result;
    }

    private List<FeatureDef> parseFeatures(Element parentEl) {
        List<FeatureDef> result = new ArrayList<>();
        for (Element el : childElements(parentEl)) {
            if ("feature".equals(el.getLocalName())) {
                String id = requireAttr(el, "id");
                boolean exclusive = "true".equals(el.getAttribute("exclusive"));
                result.add(new FeatureDef(id, exclusive));
            }
        }
        return result;
    }

    private List<DependencyDef> parseDependencies(Element parentEl) {
        List<DependencyDef> result = new ArrayList<>();
        for (Element el : childElements(parentEl)) {
            if ("component".equals(el.getLocalName())) {
                result.add(new DependencyDef(
                        requireAttr(el, "id"),
                        optAttr(el, "version"),
                        optAttr(el, "minVersion")
                ));
            }
        }
        return result;
    }

    private List<SectionDef> parseSections(Element sectionsEl) {
        List<SectionDef> result = new ArrayList<>();
        for (Element el : childElements(sectionsEl)) {
            if ("section".equals(el.getLocalName())) {
                result.add(parseSection(el));
            }
        }
        return result;
    }

    private SectionDef parseSection(Element sectionEl) {
        String name = requireAttr(sectionEl, "name");
        boolean expanded = "true".equals(requireAttr(sectionEl, "expanded"));
        String editable = requireAttr(sectionEl, "editable");
        String visible = requireAttr(sectionEl, "visible");

        String description = null;
        List<Object> children = new ArrayList<>();

        for (Element child : childElements(sectionEl)) {
            switch (child.getLocalName()) {
                case "description" -> description = child.getTextContent().trim();
                case "property" -> children.add(parseProperty(child));
                case "layout" -> children.add(parseLayout(child));
                case "image" -> children.add(parseImage(child));
            }
        }

        return new SectionDef(name, expanded, editable, visible, description, children);
    }

    private PropertyDef parseProperty(Element propEl) {
        String name = requireAttr(propEl, "name");
        PropertyDef.Type type = PropertyDef.Type.fromString(requireAttr(propEl, "type"));
        String brief = requireAttr(propEl, "brief");
        boolean required = "true".equals(requireAttr(propEl, "required"));
        String editable = requireAttr(propEl, "editable");
        String visible = requireAttr(propEl, "visible");
        String defaultValue = requireAttr(propEl, "default");

        String intMin = optAttr(propEl, "int_min");
        String intMax = optAttr(propEl, "int_max");
        String stringRegex = optAttr(propEl, "string_regex");
        String textMaxsize = optAttr(propEl, "text_maxsize");
        String enumOf = optAttr(propEl, "enum_of");
        String listColumns = optAttr(propEl, "list_columns");

        List<SectionDef> nestedSections = null;
        for (Element child : childElements(propEl)) {
            if ("sections".equals(child.getLocalName())) {
                nestedSections = parseSections(child);
            }
        }

        return new PropertyDef(name, type, brief, required, editable, visible, defaultValue,
                intMin, intMax, stringRegex, textMaxsize, enumOf, listColumns, nestedSections);
    }

    private LayoutDef parseLayout(Element layoutEl) {
        int columns = Integer.parseInt(requireAttr(layoutEl, "columns"));
        String align = requireAttr(layoutEl, "align");

        List<Object> children = new ArrayList<>();
        for (Element child : childElements(layoutEl)) {
            switch (child.getLocalName()) {
                case "property" -> children.add(parseProperty(child));
                case "image" -> children.add(parseImage(child));
                case "empty" -> children.add(LayoutDef.EmptySlot.INSTANCE);
            }
        }

        return new LayoutDef(columns, align, children);
    }

    private ImageDef parseImage(Element imageEl) {
        String file = requireAttr(imageEl, "file");
        String align = requireAttr(imageEl, "align");
        String text = null;
        for (Element child : childElements(imageEl)) {
            if ("text".equals(child.getLocalName())) {
                text = child.getTextContent().trim();
            }
        }
        return new ImageDef(file, align, text);
    }

    private static String requireAttr(Element el, String name) {
        if (!el.hasAttribute(name)) {
            throw new IllegalArgumentException(
                    "Missing required attribute '" + name + "' on element <" + el.getLocalName() + ">");
        }
        return el.getAttribute(name);
    }

    private static String optAttr(Element el, String name) {
        String value = el.getAttribute(name);
        return (value != null && !value.isEmpty()) ? value : null;
    }

    private static List<Element> childElements(Element parent) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element el) {
                result.add(el);
            }
        }
        return result;
    }
}
