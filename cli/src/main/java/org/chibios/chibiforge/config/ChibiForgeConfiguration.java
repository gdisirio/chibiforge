package org.chibios.chibiforge.config;

import java.util.List;

/**
 * Parsed representation of a chibiforge.xcfg configuration file.
 */
public class ChibiForgeConfiguration {
    private final String toolVersion;
    private final String schemaVersion;
    private final List<String> targets;
    private final List<ComponentConfigEntry> components;

    public ChibiForgeConfiguration(String toolVersion, String schemaVersion,
                                   List<String> targets, List<ComponentConfigEntry> components) {
        this.toolVersion = toolVersion;
        this.schemaVersion = schemaVersion;
        this.targets = targets != null ? List.copyOf(targets) : List.of("default");
        this.components = components != null ? List.copyOf(components) : List.of();
    }

    public String getToolVersion() { return toolVersion; }
    public String getSchemaVersion() { return schemaVersion; }
    public List<String> getTargets() { return targets; }
    public List<ComponentConfigEntry> getComponents() { return components; }

    @Override
    public String toString() {
        return "ChibiForgeConfiguration{toolVersion='" + toolVersion +
                "', targets=" + targets + ", components=" + components.size() + "}";
    }
}
