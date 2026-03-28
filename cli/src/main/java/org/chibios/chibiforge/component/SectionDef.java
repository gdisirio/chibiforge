package org.chibios.chibiforge.component;

import java.util.List;

/**
 * A section definition from a component schema.
 * Maps to {@code <section>} elements in schema.xml.
 * Children can be properties, layouts, or images (in document order).
 */
public class SectionDef {
    private final String name;
    private final boolean expanded;
    private final String description;
    private final List<Object> children; // PropertyDef, LayoutDef, or ImageDef

    public SectionDef(String name, boolean expanded, String description, List<Object> children) {
        this.name = name;
        this.expanded = expanded;
        this.description = description;
        this.children = children != null ? List.copyOf(children) : List.of();
    }

    public String getName() { return name; }
    public boolean isExpanded() { return expanded; }
    public String getDescription() { return description; }
    public List<Object> getChildren() { return children; }

    @Override
    public String toString() {
        return "SectionDef{name='" + name + "', children=" + children.size() + "}";
    }
}
