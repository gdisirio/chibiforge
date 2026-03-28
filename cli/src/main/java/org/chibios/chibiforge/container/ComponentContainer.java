package org.chibios.chibiforge.container;

import org.chibios.chibiforge.component.ComponentDefinition;

/**
 * A component container that provides access to a component's definition and content.
 * Implementations handle filesystem directories and JAR files.
 */
public interface ComponentContainer {

    /**
     * Returns the component ID (from schema.xml).
     */
    String getId();

    /**
     * Parses and returns the component definition from schema.xml.
     */
    ComponentDefinition loadDefinition() throws Exception;

    /**
     * Returns access to the component/ subtree content.
     */
    ComponentContent getComponentContent();
}
