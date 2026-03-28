package org.chibios.chibiforge.component;

/**
 * A feature declaration (requires or provides) from a component definition.
 * Maps to {@code <feature id="..." exclusive="..."/>} in schema.xml.
 */
public class FeatureDef {
    private final String id;
    private final boolean exclusive;

    public FeatureDef(String id, boolean exclusive) {
        this.id = id;
        this.exclusive = exclusive;
    }

    public String getId() { return id; }
    public boolean isExclusive() { return exclusive; }

    @Override
    public String toString() {
        return "FeatureDef{id='" + id + "', exclusive=" + exclusive + "}";
    }
}
