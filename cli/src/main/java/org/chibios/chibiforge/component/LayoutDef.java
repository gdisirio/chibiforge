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
