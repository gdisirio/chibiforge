package org.chibios.chibiforge.component;

import java.util.List;

/**
 * A layout definition from a component schema.
 * Maps to {@code <layout>} elements in schema.xml.
 * Children can be properties, images, or empty slots.
 */
public class LayoutDef {
    private final int columns;
    private final String align;
    private final List<Object> children; // PropertyDef, ImageDef, or EmptySlot

    public LayoutDef(int columns, String align, List<Object> children) {
        this.columns = columns;
        this.align = align;
        this.children = children != null ? List.copyOf(children) : List.of();
    }

    public int getColumns() { return columns; }
    public String getAlign() { return align; }
    public List<Object> getChildren() { return children; }

    /** Marker for {@code <empty/>} elements in a layout. */
    public static final class EmptySlot {
        public static final EmptySlot INSTANCE = new EmptySlot();
        private EmptySlot() {}
        @Override public String toString() { return "EmptySlot"; }
    }

    @Override
    public String toString() {
        return "LayoutDef{columns=" + columns + ", align='" + align + "', children=" + children.size() + "}";
    }
}
