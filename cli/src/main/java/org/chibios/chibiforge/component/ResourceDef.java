package org.chibios.chibiforge.component;

/**
 * A resource declaration from a component definition.
 * Maps to {@code <resource id="..." file="..."/>} in schema.xml.
 */
public class ResourceDef {
    private final String id;
    private final String file;

    public ResourceDef(String id, String file) {
        this.id = id;
        this.file = file;
    }

    public String getId() { return id; }
    public String getFile() { return file; }

    @Override
    public String toString() {
        return "ResourceDef{id='" + id + "', file='" + file + "'}";
    }
}
