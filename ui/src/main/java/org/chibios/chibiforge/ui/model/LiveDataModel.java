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

package org.chibios.chibiforge.ui.model;

import org.chibios.chibiforge.component.ComponentDefinition;
import org.chibios.chibiforge.config.ComponentConfigEntry;
import org.chibios.chibiforge.container.ComponentContainer;
import org.chibios.chibiforge.datamodel.IdNormalizer;
import org.chibios.chibiforge.resource.RefExpression;
import org.chibios.chibiforge.resource.ResourceLoader;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.util.Map;

/**
 * Maintains the live data model for a component being edited.
 * Provides @ref: resolution and @cond: XPath evaluation against the DOM.
 */
public class LiveDataModel {

    private final ComponentDefinition definition;
    private final ComponentConfigEntry configEntry;
    private final Map<String, Object> resources;
    private final XPath xpath;

    // A namespace-free copy of the config element for XPath evaluation
    private Document docForXPath;

    public LiveDataModel(ComponentDefinition definition,
                         ComponentConfigEntry configEntry,
                         ComponentContainer container) throws Exception {
        this.definition = definition;
        this.configEntry = configEntry;
        this.xpath = XPathFactory.newInstance().newXPath();

        // Load resources
        ResourceLoader loader = new ResourceLoader();
        this.resources = loader.loadResources(definition, container.getComponentContent());

        // Build the XPath-evaluable doc
        rebuildXPathDoc();
    }

    /**
     * Resolve a @ref: expression, or return the value as-is if not a @ref:.
     */
    public String resolveRef(String value) {
        if (!RefExpression.isRef(value)) return value;
        try {
            return RefExpression.resolve(value, resources);
        } catch (Exception e) {
            return value; // Return raw on failure
        }
    }

    /**
     * Evaluate a @cond: XPath expression against the live DOM.
     * Returns true if the expression evaluates to a non-empty/truthy result.
     */
    public boolean evaluateCondition(String condExpression) {
        if (condExpression == null || condExpression.isEmpty()) return true;
        try {
            // Evaluate against our namespace-free doc
            Object result = xpath.evaluate(condExpression, docForXPath, XPathConstants.STRING);
            String str = result.toString().trim();
            return "true".equalsIgnoreCase(str) || "1".equals(str);
        } catch (Exception e) {
            return true; // Default to visible/editable on error
        }
    }

    /**
     * Rebuild the XPath document from the current config element state.
     * Call this after DOM updates to ensure @cond: expressions see fresh values.
     */
    public void rebuildXPathDoc() {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            var builder = factory.newDocumentBuilder();
            docForXPath = builder.newDocument();

            // Create <doc> root with namespace-free copies of config children
            Element docRoot = docForXPath.createElement("doc");
            docForXPath.appendChild(docRoot);
            copyChildrenStrippingNamespace(configEntry.getConfigElement(), docRoot, docForXPath);
        } catch (Exception e) {
            // Fallback: empty doc
            docForXPath = null;
        }
    }

    private void copyChildrenStrippingNamespace(Element source, Element target, Document doc) {
        NodeList children = source.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element srcEl) {
                String localName = srcEl.getLocalName() != null ? srcEl.getLocalName() : srcEl.getTagName();
                Element newEl = doc.createElement(localName);
                // Copy attributes
                var attrs = srcEl.getAttributes();
                for (int j = 0; j < attrs.getLength(); j++) {
                    var attr = attrs.item(j);
                    String attrName = attr.getLocalName() != null ? attr.getLocalName() : attr.getNodeName();
                    if (!"xmlns".equals(attrName) && !attr.getNodeName().startsWith("xmlns:")) {
                        newEl.setAttribute(attrName, attr.getNodeValue());
                    }
                }
                copyChildrenStrippingNamespace(srcEl, newEl, doc);
                target.appendChild(newEl);
            } else if (child instanceof org.w3c.dom.Text) {
                target.appendChild(doc.importNode(child, true));
            }
        }
    }

    public Map<String, Object> getResources() { return resources; }
    public ComponentDefinition getDefinition() { return definition; }
}
