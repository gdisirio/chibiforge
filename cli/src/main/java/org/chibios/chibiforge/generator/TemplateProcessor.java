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
 * Processes FreeMarker templates from component template directories using FMPP.
 *
 * Supports three convention-based template directories:
 * - cfg/           → generated/          (always)
 * - cfg_root_wa/   → configRoot          (always overwrite)
 * - cfg_root_wo/   → configRoot          (write-once: skip if target exists)
 *
 * Supports .ftl (classic mode) and .ftlc (code-first mode) templates.
 */
public class TemplateProcessor {

    private static final Logger log = LoggerFactory.getLogger(TemplateProcessor.class);

    /**
     * Process all template directories for a component.
     */
    public void processTemplates(String componentId, ComponentContent content,
                                 Map<String, Object> dataModel,
                                 GenerationContext ctx,
                                 GenerationReport report) throws Exception {
        // cfg/ → generated/ (always)
        processTemplateDir(componentId, content, "cfg/", ctx.getGeneratedRoot(),
                false, dataModel, ctx, report);

        // cfg_root_wa/ → configRoot (always)
        processTemplateDir(componentId, content, "cfg_root_wa/", ctx.getConfigRoot(),
                false, dataModel, ctx, report);

        // cfg_root_wo/ → configRoot (write-once)
        processTemplateDir(componentId, content, "cfg_root_wo/", ctx.getConfigRoot(),
                true, dataModel, ctx, report);
    }

    /**
     * Process templates from a single directory.
     *
     * @param dirPrefix   template directory prefix (e.g. "cfg/", "cfg_root_wa/")
     * @param outputRoot  where FMPP writes output
     * @param writeOnce   if true, skip templates whose output file already exists
     */
    private void processTemplateDir(String componentId, ComponentContent content, String dirPrefix,
                                    Path outputRoot, boolean writeOnce,
                                    Map<String, Object> dataModel, GenerationContext ctx,
                                    GenerationReport report) throws Exception {
        List<String> templateFiles = content.list(dirPrefix);
        if (templateFiles.isEmpty()) {
            return;
        }

        // Filter to recognized template files
        List<String> templates = new ArrayList<>();
        for (String path : templateFiles) {
            String relPath = path.substring(dirPrefix.length());
            if (isTemplate(relPath)) {
                templates.add(relPath);
            } else {
                log.debug("Skipping non-template file: {}", path);
            }
        }

        if (templates.isEmpty()) {
            return;
        }

        // Write-once: filter out templates whose output already exists
        if (writeOnce) {
            List<String> filtered = new ArrayList<>();
            for (String template : templates) {
                String outputRelPath = stripTemplateExtension(template);
                Path outputPath = outputRoot.resolve(outputRelPath);
                if (Files.exists(outputPath)) {
                    log.debug("Skipping (write-once): {} -> {}", dirPrefix + template, outputPath);
                    report.addAction(new GenerationAction(
                            GenerationAction.Type.SKIP, dirPrefix + template,
                            outputPath.toString(), "write-once, exists"));
                } else {
                    filtered.add(template);
                }
            }
            templates = filtered;
            if (templates.isEmpty()) {
                return;
            }
        }

        // Handle dry-run
        if (ctx.isDryRun()) {
            for (String template : templates) {
                String outputRelPath = stripTemplateExtension(template);
                Path outputPath = outputRoot.resolve(outputRelPath);
                log.info("[dry-run] Would process template {}{} -> {}", dirPrefix, template, outputPath);
                report.addAction(new GenerationAction(
                        GenerationAction.Type.TEMPLATE, dirPrefix + template,
                        outputPath.toString(), "dry-run"));
            }
            return;
        }

        // Resolve template directory as filesystem path (extract from JAR if needed)
        Path templateDir = resolveTemplateDir(content, dirPrefix);
        boolean isTempDir = !(content instanceof FilesystemContent);

        try {
            processWithFmpp(componentId, templates, templateDir, dirPrefix, outputRoot, dataModel, ctx, report);
        } finally {
            if (isTempDir) {
                deleteTempDir(templateDir);
            }
        }
    }

    private void processWithFmpp(String componentId, List<String> templates, Path templateDir,
                                  String dirPrefix, Path outputRoot,
                                  Map<String, Object> dataModel, GenerationContext ctx,
                                  GenerationReport report) throws Exception {
        Files.createDirectories(outputRoot);

        Engine engine = new Engine();
        engine.setSourceRoot(templateDir.toFile());
        engine.setOutputRoot(outputRoot.toFile());

        engine.setRemoveFreemarkerExtensions(true);
        engine.addRemoveExtension("ftlc");

        engine.addData(dataModel);

        engine.addProgressListener(new ProgressListener() {
            @Override
            public void notifyProgressEvent(
                    Engine eng, int event, File src, int pMode,
                    Throwable error, Object param) {
                if (event == ProgressListener.EVENT_END_FILE_PROCESSING && src != null) {
                    String tmplPrefix = templateDir.toAbsolutePath().toString();
                    String srcAbs = src.getAbsolutePath();
                    String relInDir = srcAbs.startsWith(tmplPrefix)
                            ? srcAbs.substring(tmplPrefix.length() + 1) : src.getName();
                    String srcRel = dirPrefix + relInDir;
                    String outputRel = stripTemplateExtension(relInDir);
                    Path outputPath = outputRoot.resolve(outputRel);
                    log.debug("Processed template {} -> {}", srcRel, outputPath);
                    report.addAction(new GenerationAction(
                            GenerationAction.Type.TEMPLATE, srcRel,
                            outputPath.toString(), null));
                }
            }
        });

        try {
            File[] sourceFiles = templates.stream()
                    .map(t -> templateDir.resolve(t).toFile())
                    .toArray(File[]::new);
            engine.process(sourceFiles);
        } catch (ProcessingException e) {
            throw new IOException("FMPP template processing failed for component '"
                    + componentId + "' (" + dirPrefix + "): " + e.getMessage(), e);
        }
    }

    private Path resolveTemplateDir(ComponentContent content, String dirPrefix) throws IOException {
        if (content instanceof FilesystemContent fsContent) {
            return fsContent.getRoot().resolve(dirPrefix.endsWith("/")
                    ? dirPrefix.substring(0, dirPrefix.length() - 1) : dirPrefix);
        }

        // Extract template files to a temp directory
        Path tempDir = Files.createTempDirectory("chibiforge-tmpl-");
        List<String> files = content.list(dirPrefix);
        for (String relativePath : files) {
            String withinDir = relativePath.substring(dirPrefix.length());
            Path destPath = tempDir.resolve(withinDir);
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
