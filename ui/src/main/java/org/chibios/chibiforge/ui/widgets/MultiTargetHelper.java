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

package org.chibios.chibiforge.ui.widgets;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Utility for reading and writing multi-target property values in the xcfg DOM.
 *
 * Single-target format:  {@code <prop>value</prop>}
 * Multi-target format:   {@code <prop default="value"><targetValue target="t1">v1</targetValue></prop>}
 */
public class MultiTargetHelper {

    /**
     * Check if a property element is multi-target.
     * A property is multi-target if it has a "default" attribute.
     * This is true even before any targetValue overrides are added.
     */
    public static boolean isMultiTarget(Element propElement) {
        return propElement.hasAttribute("default");
    }

    /**
     * Get the resolved value for a specific target.
     * For single-target: returns the text content.
     * For multi-target: returns the targetValue match, or the default attribute.
     */
    public static String getValue(Element propElement, String target) {
        if (!isMultiTarget(propElement)) {
            return propElement.getTextContent().trim();
        }

        // Look for matching targetValue
        NodeList children = propElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el) {
                String name = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
                if ("targetValue".equals(name) && target.equals(el.getAttribute("target"))) {
                    return el.getTextContent().trim();
                }
            }
        }

        // Fall back to default
        return propElement.getAttribute("default");
    }

    /**
     * Check if the current target is using the fallback (default) value.
     */
    public static boolean isUsingFallback(Element propElement, String target) {
        if (!isMultiTarget(propElement)) return false;
        if ("default".equals(target)) return true;

        NodeList children = propElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el) {
                String name = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
                if ("targetValue".equals(name) && target.equals(el.getAttribute("target"))) {
                    return false;
                }
            }
        }
        return true; // No targetValue for this target — using fallback
    }

    /**
     * Set the value for a specific target.
     * For single-target: sets text content.
     * For multi-target: sets the matching targetValue, or the default attribute.
     */
    public static void setValue(Element propElement, String target, String value) {
        if (!isMultiTarget(propElement)) {
            propElement.setTextContent(value);
            return;
        }

        if ("default".equals(target)) {
            propElement.setAttribute("default", value);
            return;
        }

        // Find or create targetValue
        NodeList children = propElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el) {
                String name = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
                if ("targetValue".equals(name) && target.equals(el.getAttribute("target"))) {
                    el.setTextContent(value);
                    return;
                }
            }
        }

        // Create new targetValue
        Document doc = propElement.getOwnerDocument();
        Element tv = doc.createElement("targetValue");
        tv.setAttribute("target", target);
        tv.setTextContent(value);
        propElement.appendChild(tv);
    }

    /**
     * Promote a single-target property to multi-target.
     * Current text content becomes the default attribute.
     */
    public static void promoteToMultiTarget(Element propElement) {
        if (isMultiTarget(propElement)) return;
        String currentValue = propElement.getTextContent().trim();
        propElement.setAttribute("default", currentValue);
        propElement.setTextContent("");
    }

    /**
     * Demote a multi-target property to single-target.
     * The default attribute value becomes the text content.
     * All targetValue children are removed.
     */
    public static void demoteToSingleTarget(Element propElement) {
        if (!isMultiTarget(propElement)) return;
        String defaultValue = propElement.getAttribute("default");

        // Remove all targetValue children
        NodeList children = propElement.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            if (children.item(i) instanceof Element el) {
                String name = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
                if ("targetValue".equals(name)) {
                    propElement.removeChild(el);
                }
            }
        }

        propElement.removeAttribute("default");
        propElement.setTextContent(defaultValue);
    }

    /**
     * Get all target values for a multi-target property.
     * Returns map of target -> value. Includes "default" key.
     */
    public static Map<String, String> getAllTargetValues(Element propElement) {
        Map<String, String> values = new LinkedHashMap<>();
        if (!isMultiTarget(propElement)) {
            return values;
        }

        values.put("default", propElement.getAttribute("default"));

        NodeList children = propElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el) {
                String name = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
                if ("targetValue".equals(name)) {
                    values.put(el.getAttribute("target"), el.getTextContent().trim());
                }
            }
        }

        return values;
    }
}
