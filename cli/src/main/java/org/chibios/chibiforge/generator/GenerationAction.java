package org.chibios.chibiforge.generator;

/**
 * Describes a single action taken (or planned) during generation.
 */
public class GenerationAction {

    public enum Type {
        COPY,       // Static file copied
        SKIP,       // Static file skipped (write-once, already exists)
        TEMPLATE    // Template processed
    }

    private final Type type;
    private final String source;
    private final String destination;
    private final String reason;

    public GenerationAction(Type type, String source, String destination, String reason) {
        this.type = type;
        this.source = source;
        this.destination = destination;
        this.reason = reason;
    }

    public Type getType() { return type; }
    public String getSource() { return source; }
    public String getDestination() { return destination; }
    public String getReason() { return reason; }

    @Override
    public String toString() {
        return type + ": " + source + " -> " + destination +
                (reason != null ? " (" + reason + ")" : "");
    }
}
