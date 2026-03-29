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

package org.chibios.chibiforge.generator;

import fmpp.Engine;
import fmpp.ProgressListener;
import fmpp.ProcessingException;
import org.chibios.chibiforge.container.ComponentContent;
import org.chibios.chibiforge.container.FilesystemContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Processes FreeMarker templates from component cfg/ directory using FMPP.
 *
 * Supports .ftl (classic mode) and .ftlc (code-first mode) templates.
 * FMPP provides the pp hash for output redirection and handles extension stripping.
 *
 * Output mapping: cfg/foo.h.ftl -> generated/foo.h (FMPP strips template extensions).
 * Templates can use pp.changeOutputFile to redirect output within the configuration root.
 */
public class TemplateProcessor {

    private static final Logger log = LoggerFactory.getLogger(TemplateProcessor.class);

    public void processTemplates(String componentId, ComponentContent content,
                                 Map<String, Object> dataModel,
                                 GenerationContext ctx,
                                 GenerationReport report) throws Exception {
        List<String> templateFiles = content.list("cfg/");
        if (templateFiles.isEmpty()) {
            log.debug("No templates found for component '{}'", componentId);
            return;
        }

        // Filter to recognized template files
        List<String> templates = new ArrayList<>();
        for (String path : templateFiles) {
            String relPath = path.substring("cfg/".length());
            if (isTemplate(relPath)) {
                templates.add(relPath);
            } else {
                log.debug("Skipping non-template file: {}", path);
            }
        }

        if (templates.isEmpty()) {
            return;
        }

        // Handle dry-run: just report what would happen
        if (ctx.isDryRun()) {
            for (String template : templates) {
                String outputRelPath = stripTemplateExtension(template);
                Path outputPath = ctx.getGeneratedRoot().resolve(outputRelPath);
                log.info("[dry-run] Would process template cfg/{} -> {}", template, outputPath);
                report.addAction(new GenerationAction(
                        GenerationAction.Type.TEMPLATE, "cfg/" + template,
                        outputPath.toString(), "dry-run"));
            }
            return;
        }

        // Resolve cfg/ as a filesystem directory (extract from JAR if needed)
        Path cfgDir = resolveCfgDir(content);
        boolean isTempDir = !(content instanceof FilesystemContent);

        try {
            processWithFmpp(componentId, templates, cfgDir, dataModel, ctx, report);
        } finally {
            if (isTempDir) {
                deleteTempDir(cfgDir);
            }
        }
    }

    private void processWithFmpp(String componentId, List<String> templates, Path cfgDir,
                                  Map<String, Object> dataModel, GenerationContext ctx,
                                  GenerationReport report) throws Exception {
        Files.createDirectories(ctx.getGeneratedRoot());

        Engine engine = new Engine();
        engine.setSourceRoot(cfgDir.toFile());
        engine.setOutputRoot(ctx.getGeneratedRoot().toFile());

        engine.setRemoveFreemarkerExtensions(true);
        engine.addRemoveExtension("ftlc");

        engine.addData(dataModel);

        engine.addProgressListener(new ProgressListener() {
            @Override
            public void notifyProgressEvent(
                    Engine eng, int event, File src, int pMode,
                    Throwable error, Object param) {
                if (event == ProgressListener.EVENT_END_FILE_PROCESSING && src != null) {
                    String cfgPrefix = cfgDir.toAbsolutePath().toString();
                    String srcAbs = src.getAbsolutePath();
                    String relInCfg = srcAbs.startsWith(cfgPrefix)
                            ? srcAbs.substring(cfgPrefix.length() + 1) : src.getName();
                    String srcRel = "cfg/" + relInCfg;
                    String outputRel = stripTemplateExtension(relInCfg);
                    Path outputPath = ctx.getGeneratedRoot().resolve(outputRel);
                    log.debug("Processed template {} -> {}", srcRel, outputPath);
                    report.addAction(new GenerationAction(
                            GenerationAction.Type.TEMPLATE, srcRel,
                            outputPath.toString(), null));
                }
            }
        });

        try {
            File[] sourceFiles = templates.stream()
                    .map(t -> cfgDir.resolve(t).toFile())
                    .toArray(File[]::new);
            engine.process(sourceFiles);
        } catch (ProcessingException e) {
            throw new IOException("FMPP template processing failed for component '"
                    + componentId + "': " + e.getMessage(), e);
        }
    }

    /**
     * Resolve the cfg/ directory as a filesystem path.
     * For FilesystemContent, returns the path directly.
     * For other content types (JAR), extracts cfg/ files to a temp directory.
     */
    private Path resolveCfgDir(ComponentContent content) throws IOException {
        if (content instanceof FilesystemContent fsContent) {
            return fsContent.getRoot().resolve("cfg");
        }

        // Extract cfg/ files to a temp directory
        Path tempDir = Files.createTempDirectory("chibiforge-cfg-");
        List<String> cfgFiles = content.list("cfg/");
        for (String relativePath : cfgFiles) {
            String withinCfg = relativePath.substring("cfg/".length());
            Path destPath = tempDir.resolve(withinCfg);
            Files.createDirectories(destPath.getParent());
            try (InputStream is = content.open(relativePath)) {
                Files.copy(is, destPath);
            }
        }
        return tempDir;
    }

    private void deleteTempDir(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.delete(p); } catch (IOException ignored) {}
                });
        } catch (IOException ignored) {}
    }

    private boolean isTemplate(String path) {
        return path.endsWith(".ftl") || path.endsWith(".ftlc");
    }

    /**
     * Strip the template extension (.ftl or .ftlc) from the filename.
     */
    private String stripTemplateExtension(String path) {
        if (path.endsWith(".ftl")) {
            return path.substring(0, path.length() - ".ftl".length());
        }
        if (path.endsWith(".ftlc")) {
            return path.substring(0, path.length() - ".ftlc".length());
        }
        return path;
    }
}
