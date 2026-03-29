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

package org.chibios.chibiforge.cli;

import org.chibios.chibiforge.generator.GenerationAction;
import org.chibios.chibiforge.generator.GenerationContext;
import org.chibios.chibiforge.generator.GenerationReport;
import org.chibios.chibiforge.generator.GeneratorEngine;
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
            Path resolvedPlugins = resolvePluginsRoot();

            if (!Files.exists(resolvedConfig)) {
                System.err.println("Error: Configuration file not found: " + resolvedConfig);
                return 1;
            }
            if (!Files.isRegularFile(resolvedConfig)) {
                System.err.println("Error: Configuration path is not a file: " + resolvedConfig);
                return 1;
            }
            if (resolvedComponents != null) {
                if (!Files.exists(resolvedComponents)) {
                    System.err.println("Error: Components root not found: " + resolvedComponents);
                    return 1;
                }
                if (!Files.isDirectory(resolvedComponents)) {
                    System.err.println("Error: Components root is not a directory: " + resolvedComponents);
                    return 1;
                }
            }
            if (resolvedPlugins != null) {
                if (!Files.exists(resolvedPlugins)) {
                    System.err.println("Error: Plugins root not found: " + resolvedPlugins);
                    return 1;
                }
                if (!Files.isDirectory(resolvedPlugins)) {
                    System.err.println("Error: Plugins root is not a directory: " + resolvedPlugins);
                    return 1;
                }
            }
            if (resolvedComponents == null && resolvedPlugins == null) {
                System.err.println("Error: No component sources specified. " +
                        "Use --components and/or --plugins (or set CHIBIFORGE_COMPONENTS_ROOT / CHIBIFORGE_PLUGINS_ROOT)");
                return 1;
            }

            Path configRoot = resolvedConfig.getParent();
            if (configRoot == null) {
                configRoot = Path.of(".");
            }

            if (verbose) {
                System.out.println("Configuration file: " + resolvedConfig.toAbsolutePath());
                System.out.println("Configuration root: " + configRoot.toAbsolutePath());
                if (resolvedComponents != null)
                    System.out.println("Components root:    " + resolvedComponents.toAbsolutePath());
                if (resolvedPlugins != null)
                    System.out.println("Plugins root:       " + resolvedPlugins.toAbsolutePath());
                System.out.println("Target:             " + target);
                System.out.println("Dry run:            " + dryRun);
            }

            GenerationContext ctx = new GenerationContext(configRoot, target, dryRun, verbose);
            GeneratorEngine engine = new GeneratorEngine();
            GenerationReport report = engine.generate(ctx, resolvedComponents, resolvedPlugins);

            if (verbose) {
                report.printSummary();
            } else {
                for (String warning : report.getWarnings()) {
                    System.out.println("WARNING: " + warning);
                }
                System.out.println("Generation complete: " +
                        report.countByType(GenerationAction.Type.COPY) + " copied, " +
                        report.countByType(GenerationAction.Type.SKIP) + " skipped, " +
                        report.countByType(GenerationAction.Type.TEMPLATE) + " templates.");
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
        return null;
    }

    private Path resolvePluginsRoot() {
        if (pluginsRoot != null) {
            return Path.of(pluginsRoot);
        }
        String envRoot = System.getenv("CHIBIFORGE_PLUGINS_ROOT");
        if (envRoot != null && !envRoot.isBlank()) {
            return Path.of(envRoot);
        }
        return null;
    }
}
