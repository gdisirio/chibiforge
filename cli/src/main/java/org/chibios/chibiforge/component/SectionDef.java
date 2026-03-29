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
