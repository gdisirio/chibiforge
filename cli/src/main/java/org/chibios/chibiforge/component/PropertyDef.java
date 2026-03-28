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

    private final String name;
    private final Type type;
    private final String brief;
    private final boolean required;
    private final boolean editable;
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

    public PropertyDef(String name, Type type, String brief, boolean required, boolean editable,
                       String defaultValue, String intMin, String intMax, String stringRegex,
                       String textMaxsize, String enumOf, String listColumns,
                       List<SectionDef> nestedSections) {
        this.name = name;
        this.type = type;
        this.brief = brief;
        this.required = required;
        this.editable = editable;
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
    public boolean isEditable() { return editable; }
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
