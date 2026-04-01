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
 * A property definition from a component schema.
 * Maps to {@code <property>} elements in schema.xml.
 */
public class PropertyDef {

    public enum Type {
        BOOL, STRING, TEXT, INT, ENUM, LIST;

        public static Type fromString(String s) {
            return valueOf(s.toUpperCase());
        }
    }

    private static final String COND_PREFIX = "@cond:";

    private final String name;
    private final Type type;
    private final String brief;
    private final boolean required;
    private final String editable;   // "true", "false", or "@cond:<xpath>"
    private final String visible;    // "true", "false", or "@cond:<xpath>"
    private final String defaultValue;

    // Type-specific attributes
    private final String intMin;
    private final String intMax;
    private final String stringRegex;
    private final String textMaxsize;
    private final String enumOf;
    private final String listColumns;

    // Nested sections for list type
    private final List<SectionDef> nestedSections;

    public PropertyDef(String name, Type type, String brief, boolean required,
                       String editable, String visible,
                       String defaultValue, String intMin, String intMax, String stringRegex,
                       String textMaxsize, String enumOf, String listColumns,
                       List<SectionDef> nestedSections) {
        this.name = name;
        this.type = type;
        this.brief = brief;
        this.required = required;
        this.editable = editable != null ? editable : "true";
        this.visible = visible != null ? visible : "true";
        this.defaultValue = defaultValue;
        this.intMin = intMin;
        this.intMax = intMax;
        this.stringRegex = stringRegex;
        this.textMaxsize = textMaxsize;
        this.enumOf = enumOf;
        this.listColumns = listColumns;
        this.nestedSections = nestedSections != null ? List.copyOf(nestedSections) : List.of();
    }

    public String getName() { return name; }
    public Type getType() { return type; }
    public String getBrief() { return brief; }
    public boolean isRequired() { return required; }

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

    public String getDefaultValue() { return defaultValue; }
    public String getIntMin() { return intMin; }
    public String getIntMax() { return intMax; }
    public String getStringRegex() { return stringRegex; }
    public String getTextMaxsize() { return textMaxsize; }
    public String getEnumOf() { return enumOf; }
    public String getListColumns() { return listColumns; }
    public List<SectionDef> getNestedSections() { return nestedSections; }

    @Override
    public String toString() {
        return "PropertyDef{name='" + name + "', type=" + type + "}";
    }
}
