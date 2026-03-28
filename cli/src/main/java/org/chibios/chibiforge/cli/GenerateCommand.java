package org.chibios.chibiforge.cli;

import org.chibios.chibiforge.generator.GenerationContext;
import org.chibios.chibiforge.generator.GenerationReport;
import org.chibios.chibiforge.generator.GeneratorEngine;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

@Command(
        name = "generate",
        description = "Generate configuration and code from a ChibiForge configuration file",
        mixinStandardHelpOptions = true
)
public class GenerateCommand implements Callable<Integer> {

    @Option(names = {"--config", "-c"},
            description = "Path to chibiforge.xcfg (default: env CHIBIFORGE_CONFIG_PATH or ./chibiforge.xcfg)")
    private String configPath;

    @Option(names = {"--components"},
            description = "Filesystem components root (default: env CHIBIFORGE_COMPONENTS_ROOT)")
    private String componentsRoot;

    @Option(names = {"--plugins"},
            description = "Plugin JARs root (default: env CHIBIFORGE_PLUGINS_ROOT)")
    private String pluginsRoot;

    @Option(names = {"--target", "-t"},
            description = "Target to generate for (default: \"default\")",
            defaultValue = "default")
    private String target;

    @Option(names = {"--dry-run"},
            description = "Log intended actions without writing files")
    private boolean dryRun;

    @Option(names = {"--verbose", "-v"},
            description = "Extended logging")
    private boolean verbose;

    @Override
    public Integer call() {
        try {
            Path resolvedConfig = resolveConfigPath();
            Path resolvedComponents = resolveComponentsRoot();

            if (!Files.exists(resolvedConfig)) {
                System.err.println("Error: Configuration file not found: " + resolvedConfig);
                return 1;
            }
            if (!Files.isRegularFile(resolvedConfig)) {
                System.err.println("Error: Configuration path is not a file: " + resolvedConfig);
                return 1;
            }
            if (!Files.exists(resolvedComponents)) {
                System.err.println("Error: Components root not found: " + resolvedComponents);
                return 1;
            }
            if (!Files.isDirectory(resolvedComponents)) {
                System.err.println("Error: Components root is not a directory: " + resolvedComponents);
                return 1;
            }

            Path configRoot = resolvedConfig.getParent();
            if (configRoot == null) {
                configRoot = Path.of(".");
            }

            if (verbose) {
                System.out.println("Configuration file: " + resolvedConfig.toAbsolutePath());
                System.out.println("Configuration root: " + configRoot.toAbsolutePath());
                System.out.println("Components root:    " + resolvedComponents.toAbsolutePath());
                System.out.println("Target:             " + target);
                System.out.println("Dry run:            " + dryRun);
            }

            GenerationContext ctx = new GenerationContext(configRoot, target, dryRun, verbose);
            GeneratorEngine engine = new GeneratorEngine();
            GenerationReport report = engine.generate(ctx, resolvedComponents);

            if (verbose) {
                report.printSummary();
            } else {
                for (String warning : report.getWarnings()) {
                    System.out.println("WARNING: " + warning);
                }
                System.out.println("Generation complete: " +
                        report.countByType(org.chibios.chibiforge.generator.GenerationAction.Type.COPY) + " copied, " +
                        report.countByType(org.chibios.chibiforge.generator.GenerationAction.Type.SKIP) + " skipped, " +
                        report.countByType(org.chibios.chibiforge.generator.GenerationAction.Type.TEMPLATE) + " templates.");
            }
            return 0;

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }

    private Path resolveConfigPath() {
        if (configPath != null) {
            return Path.of(configPath);
        }
        String envPath = System.getenv("CHIBIFORGE_CONFIG_PATH");
        if (envPath != null && !envPath.isBlank()) {
            return Path.of(envPath);
        }
        return Path.of("chibiforge.xcfg");
    }

    private Path resolveComponentsRoot() {
        if (componentsRoot != null) {
            return Path.of(componentsRoot);
        }
        String envRoot = System.getenv("CHIBIFORGE_COMPONENTS_ROOT");
        if (envRoot != null && !envRoot.isBlank()) {
            return Path.of(envRoot);
        }
        throw new IllegalArgumentException(
                "No components root specified. Use --components or set CHIBIFORGE_COMPONENTS_ROOT");
    }

    // Getters for testing
    public String getTarget() { return target; }
    public boolean isDryRun() { return dryRun; }
    public boolean isVerbose() { return verbose; }
}
