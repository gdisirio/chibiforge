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

import org.chibios.chibiforge.ChibiForgeException;
import org.chibios.chibiforge.generator.GenerationAction;
import org.chibios.chibiforge.generator.GenerationContext;
import org.chibios.chibiforge.generator.GenerationReport;
import org.chibios.chibiforge.generator.GeneratorEngine;
import org.chibios.chibiforge.sources.ComponentSourceResolver;
import org.chibios.chibiforge.sources.ResolvedComponentSources;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "generate",
        description = "Generate configuration and code from a ChibiForge configuration file",
        mixinStandardHelpOptions = true
)
public class GenerateCommand implements Callable<Integer> {

    @Option(names = {"--project", "-p"},
            description = "Path to the project root (default: env CHIBIFORGE_PROJECT_ROOT or current directory)")
    private String projectRoot;

    @Option(names = {"--components"},
            description = "Preferred filesystem components root (overrides auto-discovered roots; default: env CHIBIFORGE_COMPONENTS_ROOT)")
    private String componentsRoot;

    @Option(names = {"--plugins"},
            description = "Preferred plugin JAR root or file (overrides auto-discovered roots; default: env CHIBIFORGE_PLUGINS_ROOT)")
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
            Path resolvedProjectRoot = resolveProjectRoot().toAbsolutePath().normalize();
            Path resolvedConfig = resolvedProjectRoot.resolve("chibiforge.xcfg");

            if (!Files.exists(resolvedConfig)) {
                System.err.println("Error: Configuration file not found: " + resolvedConfig);
                return 1;
            }
            if (!Files.isRegularFile(resolvedConfig)) {
                System.err.println("Error: Configuration path is not a file: " + resolvedConfig);
                return 1;
            }

            Path configRoot = resolvedProjectRoot;

            ResolvedComponentSources resolvedSources = resolveComponentSources(resolvedConfig);
            for (String warning : resolvedSources.warnings()) {
                System.err.println("WARNING: " + warning);
            }
            if (resolvedSources.roots().isEmpty()) {
                System.err.println("Error: No component sources resolved. " +
                        "Use --components/--plugins, add chibiforge_sources.json, create ./components, " +
                        "or set CHIBIFORGE_COMPONENTS.");
                return 1;
            }

            if (verbose) {
                System.out.println("Project root:       " + resolvedProjectRoot);
                System.out.println("Configuration file: " + resolvedConfig.toAbsolutePath());
                for (Path componentRoot : resolvedSources.roots()) {
                    System.out.println("Component root:     " + componentRoot.toAbsolutePath());
                }
                System.out.println("Target:             " + target);
                System.out.println("Dry run:            " + dryRun);
            }

            GenerationContext ctx = new GenerationContext(resolvedConfig, configRoot, target, dryRun, verbose);
            GeneratorEngine engine = new GeneratorEngine();
            GenerationReport report = engine.generate(ctx, resolvedSources.roots());

            // Warnings to stderr
            for (String warning : report.getWarnings()) {
                System.err.println("WARNING: " + warning);
            }

            if (verbose) {
                report.printSummary();
            } else {
                System.out.println("Generation complete: " +
                        report.countByType(GenerationAction.Type.COPY) + " copied, " +
                        report.countByType(GenerationAction.Type.SKIP) + " skipped, " +
                        report.countByType(GenerationAction.Type.TEMPLATE) + " templates.");
            }
            return 0;

        } catch (ChibiForgeException e) {
            System.err.println("Error: " + e.getMessage());
            if (verbose && e.getCause() != null) {
                e.getCause().printStackTrace(System.err);
            }
            return 1;
        } catch (Exception e) {
            System.err.println("Internal error: " + e.getMessage());
            if (verbose) {
                e.printStackTrace(System.err);
            }
            return 1;
        }
    }

    private Path resolveProjectRoot() {
        if (projectRoot != null) {
            return Path.of(projectRoot);
        }
        String envRoot = System.getenv("CHIBIFORGE_PROJECT_ROOT");
        if (envRoot != null && !envRoot.isBlank()) {
            return Path.of(envRoot);
        }
        return Path.of(".");
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

    private ResolvedComponentSources resolveComponentSources(Path configFile) {
        List<Path> preferredRoots = new ArrayList<>();

        Path resolvedComponents = resolveComponentsRoot();
        if (resolvedComponents != null) {
            preferredRoots.add(resolvedComponents);
        }

        Path resolvedPlugins = resolvePluginsRoot();
        if (resolvedPlugins != null) {
            preferredRoots.add(resolvedPlugins);
        }

        return new ComponentSourceResolver().resolve(configFile, preferredRoots);
    }
}
