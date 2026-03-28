package org.chibios.chibiforge.component;

/**
 * An image definition from a component schema.
 * Maps to {@code <image>} elements in schema.xml.
 */
public class ImageDef {
    private final String file;
    private final String align;
    private final String text;

    public ImageDef(String file, String align, String text) {
        this.file = file;
        this.align = align;
        this.text = text;
    }

    public String getFile() { return file; }
    public String getAlign() { return align; }
    public String getText() { return text; }

    @Override
    public String toString() {
        return "ImageDef{file='" + file + "', align='" + align + "'}";
    }
}
