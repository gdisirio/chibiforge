package org.chibios.chibiforge.config;

import org.w3c.dom.Element;

/**
 * A single component's configuration values from chibiforge.xcfg.
 * Stores the component ID and the raw DOM element containing section/property values.
 */
public class ComponentConfigEntry {
    private final String componentId;
    private final Element configElement;

    public ComponentConfigEntry(String componentId, Element configElement) {
        this.componentId = componentId;
        this.configElement = configElement;
    }

    public String getComponentId() { return componentId; }

    /**
     * Returns the raw {@code <component>} element from the xcfg file.
     * Children are section elements containing property values.
     */
    public Element getConfigElement() { return configElement; }

    @Override
    public String toString() {
        return "ComponentConfigEntry{componentId='" + componentId + "'}";
    }
}
