package org.chibios.chibiforge.generator;

import java.nio.file.Path;

/**
 * Runtime context for a generation run.
 */
public class GenerationContext {
    private final Path configRoot;
    private final Path generatedRoot;
    private final String target;
    private final boolean dryRun;
    private final boolean verbose;

    public GenerationContext(Path configRoot, String target, boolean dryRun, boolean verbose) {
        this.configRoot = configRoot;
        this.generatedRoot = configRoot.resolve("generated");
        this.target = target;
        this.dryRun = dryRun;
        this.verbose = verbose;
    }

    public Path getConfigRoot() { return configRoot; }
    public Path getGeneratedRoot() { return generatedRoot; }
    public String getTarget() { return target; }
    public boolean isDryRun() { return dryRun; }
    public boolean isVerbose() { return verbose; }
}
