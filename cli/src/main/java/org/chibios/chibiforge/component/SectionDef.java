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

    private static final String COND_PREFIX = "@cond:";

    private final String name;
    private final boolean expanded;
    private final String editable;   // "true", "false", or "@cond:<xpath>"
    private final String visible;    // "true", "false", or "@cond:<xpath>"
    private final String description;
    private final List<Object> children; // PropertyDef, LayoutDef, or ImageDef

    public SectionDef(String name, boolean expanded, String editable, String visible,
                      String description, List<Object> children) {
        this.name = name;
        this.expanded = expanded;
        this.editable = editable != null ? editable : "true";
        this.visible = visible != null ? visible : "true";
        this.description = description;
        this.children = children != null ? List.copyOf(children) : List.of();
    }

    public String getName() { return name; }
    public boolean isExpanded() { return expanded; }
    public String getDescription() { return description; }
    public List<Object> getChildren() { return children; }

    /** Raw editable value: "true", "false", or "@cond:xpath". */
    public String getEditable() { return editable; }
    /** True if statically editable (no @cond:). */
    public boolean isEditable() { return "true".equals(editable); }
    /** True if editable has a @cond: expression. */
    public boolean hasEditableCondition() { return editable.startsWith(COND_PREFIX); }
    /** Returns the @cond: XPath expression, or null. */
    public String getEditableCondition() {
        return hasEditableCondition() ? editable.substring(COND_PREFIX.length()) : null;
    }

    /** Raw visible value: "true", "false", or "@cond:xpath". */
    public String getVisible() { return visible; }
    /** True if statically visible (no @cond:). */
    public boolean isVisible() { return "true".equals(visible); }
    /** True if visible has a @cond: expression. */
    public boolean hasVisibleCondition() { return visible.startsWith(COND_PREFIX); }
    /** Returns the @cond: XPath expression, or null. */
    public String getVisibleCondition() {
        return hasVisibleCondition() ? visible.substring(COND_PREFIX.length()) : null;
    }

    @Override
    public String toString() {
        return "SectionDef{name='" + name + "', children=" + children.size() + "}";
    }
}
