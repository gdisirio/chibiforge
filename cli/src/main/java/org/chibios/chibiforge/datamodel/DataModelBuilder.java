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

package org.chibios.chibiforge.datamodel;

import freemarker.ext.dom.NodeModel;
import org.chibios.chibiforge.config.ComponentConfigEntry;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the FMPP-compatible data model for template processing.
 *
 * Top-level variables:
 * - doc: current component's resolved configuration (wrapped XML)
 * - components: all components' resolved configurations keyed by normalized ID
 * - configuration: metadata (root, generatedRoot, target)
 * - One variable per resource
 */
public class DataModelBuilder {

    /**
     * Build the complete data model for one component's template processing.
     *
     * @param componentId the current component's ID
     * @param configEntry the current component's config from xcfg
     * @param allConfigs all component configs from xcfg, keyed by component ID
     * @param resources loaded resources for this component (resource ID -> Document/JsonNode)
     * @param configRoot the configuration root directory
     * @param target the active target name
     * @return data model map suitable for FMPP/FreeMarker
     */
    public Map<String, Object> buildDataModel(
            String componentId,
            ComponentConfigEntry configEntry,
            Map<String, ComponentConfigEntry> allConfigs,
            Map<String, Object> resources,
            Path configRoot,
            String target) throws Exception {

        Map<String, Object> dataModel = new LinkedHashMap<>();

        // doc: current component's config (multi-target properties resolved)
        dataModel.put("doc", buildDoc(configEntry, target));

        // components: all configs keyed by normalized ID (multi-target resolved)
        dataModel.put("components", buildComponents(allConfigs, target));

        // configuration: metadata
        dataModel.put("configuration", buildConfiguration(configRoot, target));

        // Resource variables
        for (Map.Entry<String, Object> entry : resources.entrySet()) {
            Object resource = entry.getValue();
            if (resource instanceof Document xmlDoc) {
                dataModel.put(entry.getKey(), NodeModel.wrap(xmlDoc));
            } else {
                // JSON resources: store as-is for now (FreeMarker can handle via BeansWrapper)
                dataModel.put(entry.getKey(), resource);
            }
        }

        return dataModel;
    }

    private Object buildDoc(ComponentConfigEntry configEntry, String target) throws Exception {
        Document doc = createDocument();
        Element docRoot = doc.createElement("doc");
        doc.appendChild(docRoot);

        Element sourceEl = configEntry.getConfigElement();
        copyChildrenResolvingTargets(sourceEl, docRoot, doc, target);

        return NodeModel.wrap(docRoot);
    }

    private Object buildComponents(Map<String, ComponentConfigEntry> allConfigs, String target) throws Exception {
        Document doc = createDocument();
        Element componentsRoot = doc.createElement("components");
        doc.appendChild(componentsRoot);

        for (Map.Entry<String, ComponentConfigEntry> entry : allConfigs.entrySet()) {
            String normalizedId = IdNormalizer.normalize(entry.getKey());
            Element compEl = doc.createElement(normalizedId);
            componentsRoot.appendChild(compEl);

            Element sourceEl = entry.getValue().getConfigElement();
            copyChildrenResolvingTargets(sourceEl, compEl, doc, target);
        }

        return NodeModel.wrap(componentsRoot);
    }

    private Object buildConfiguration(Path configRoot, String target) throws Exception {
        Document doc = createDocument();
        Element root = doc.createElement("configuration");
        doc.appendChild(root);

        Element rootEl = doc.createElement("root");
        rootEl.setTextContent(configRoot.toAbsolutePath().toString());
        root.appendChild(rootEl);

        Element genRootEl = doc.createElement("generatedRoot");
        genRootEl.setTextContent(configRoot.resolve("generated").toAbsolutePath().toString());
        root.appendChild(genRootEl);

        Element targetEl = doc.createElement("target");
        targetEl.setTextContent(target);
        root.appendChild(targetEl);

        return NodeModel.wrap(root);
    }

    /**
     * Recursively copies child nodes from source to target, stripping XML namespaces
     * and resolving multi-target properties for the active target.
     *
     * Multi-target property format:
     * {@code <vdd default="300"><targetValue target="debug">330</targetValue></vdd>}
     *
     * Resolution: if an element has a "default" attribute and {@code <targetValue>} children,
     * it is a multi-target property. The resolved value replaces the entire element content.
     */
    private void copyChildrenResolvingTargets(Element source, Element target, Document doc, String activeTarget) {
        NodeList children = source.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element srcEl) {
                String localName = srcEl.getLocalName() != null ? srcEl.getLocalName() : srcEl.getTagName();
                Element newEl = doc.createElement(localName);

                // Check if this is a multi-target property
                String resolvedValue = resolveMultiTargetValue(srcEl, activeTarget);
                if (resolvedValue != null) {
                    // Multi-target: emit as plain text element with resolved value
                    newEl.setTextContent(resolvedValue);
                } else {
                    // Regular element: copy attributes and recurse into children
                    NamedNodeMap attrs = srcEl.getAttributes();
                    for (int j = 0; j < attrs.getLength(); j++) {
                        Node attr = attrs.item(j);
                        String attrName = attr.getLocalName() != null ? attr.getLocalName() : attr.getNodeName();
                        if (!"xmlns".equals(attrName) && !attr.getNodeName().startsWith("xmlns:")) {
                            newEl.setAttribute(attrName, attr.getNodeValue());
                        }
                    }
                    copyChildrenResolvingTargets(srcEl, newEl, doc, activeTarget);
                }

                target.appendChild(newEl);
            } else if (child instanceof Text) {
                target.appendChild(doc.importNode(child, true));
            }
        }
    }

    /**
     * If the element is a multi-target property, resolve the value for the active target.
     * Returns null if the element is not a multi-target property.
     *
     * A multi-target property has a "default" attribute and at least one {@code <targetValue>} child.
     */
    private String resolveMultiTargetValue(Element el, String activeTarget) {
        if (!el.hasAttribute("default")) {
            return null;
        }

        // Look for <targetValue> children
        boolean hasTargetValues = false;
        String matchedValue = null;

        NodeList children = el.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child) {
                String childName = child.getLocalName() != null ? child.getLocalName() : child.getTagName();
                if ("targetValue".equals(childName)) {
                    hasTargetValues = true;
                    String targetAttr = child.getAttribute("target");
                    if (activeTarget.equals(targetAttr)) {
                        matchedValue = child.getTextContent().trim();
                    }
                }
            }
        }

        if (!hasTargetValues) {
            return null; // Has "default" attr but no <targetValue> children — not multi-target
        }

        // Multi-target: return matched value or fall back to default
        return matchedValue != null ? matchedValue : el.getAttribute("default");
    }

    private Document createDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.newDocument();
    }
}
