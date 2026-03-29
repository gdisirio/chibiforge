package org.chibios.chibiforge.resource;

import org.chibios.chibiforge.component.ComponentDefinition;
import org.chibios.chibiforge.component.PropertyDef;
import org.chibios.chibiforge.component.SectionDef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.util.Map;

/**
 * Resolves @ref: expressions in component property defaults and applies
 * missing default values to the configuration element.
 *
 * When a property defined in schema.xml has a default (possibly an @ref: expression)
 * but the xcfg configuration element has no corresponding child, this resolver
 * creates the missing element with the resolved default value.
 */
public class RefResolver {

    private static final Logger log = LoggerFactory.getLogger(RefResolver.class);

    /**
     * Walk the component definition and fill in missing property defaults in the config element.
     * @ref: expressions in default values are resolved against loaded resources.
     *
     * @param def the component definition (schema)
     * @param configElement the component's config element from xcfg (modified in place)
     * @param resources loaded resources for this component
     */
    public void applyDefaults(ComponentDefinition def, Element configElement,
                              Map<String, Object> resources) {
        for (SectionDef section : def.getSections()) {
            applySectionDefaults(section, configElement, resources);
        }
    }

    private void applySectionDefaults(SectionDef section, Element parentElement,
                                      Map<String, Object> resources) {
        // Find or skip the section element in the config
        String sectionName = section.getName().toLowerCase().replaceAll("[^a-z0-9_]", "_").replaceAll("_+", "_");
        Element sectionElement = findChildElement(parentElement, sectionName);

        if (sectionElement == null) {
            // Section not present in config — create it with all defaults
            Document doc = parentElement.getOwnerDocument();
            sectionElement = doc.createElement(sectionName);
            parentElement.appendChild(sectionElement);
        }

        for (Object child : section.getChildren()) {
            if (child instanceof PropertyDef prop) {
                applyPropertyDefault(prop, sectionElement, resources);
            }
            // Layouts contain properties too
            if (child instanceof org.chibios.chibiforge.component.LayoutDef layout) {
                for (Object layoutChild : layout.getChildren()) {
                    if (layoutChild instanceof PropertyDef prop) {
                        applyPropertyDefault(prop, sectionElement, resources);
                    }
                }
            }
        }
    }

    private void applyPropertyDefault(PropertyDef prop, Element sectionElement,
                                      Map<String, Object> resources) {
        String propName = prop.getName();
        Element propElement = findChildElement(sectionElement, propName);

        if (propElement != null) {
            // Property exists in config — don't override
            return;
        }

        // Property missing from config — apply default
        String defaultValue = prop.getDefaultValue();
        if (defaultValue == null || defaultValue.isEmpty()) {
            return;
        }

        // Resolve @ref: if present
        String resolved = defaultValue;
        if (RefExpression.isRef(defaultValue)) {
            try {
                resolved = RefExpression.resolve(defaultValue, resources);
                log.debug("Resolved default for '{}': {} -> {}", propName, defaultValue, resolved);
            } catch (Exception e) {
                log.warn("Failed to resolve @ref: default for '{}': {}", propName, e.getMessage());
                return;
            }
        }

        // Create the property element with the resolved value
        Document doc = sectionElement.getOwnerDocument();
        Element newProp = doc.createElement(propName);
        newProp.setTextContent(resolved);
        sectionElement.appendChild(newProp);
    }

    private Element findChildElement(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el) {
                String localName = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
                if (name.equals(localName)) {
                    return el;
                }
            }
        }
        return null;
    }
}
