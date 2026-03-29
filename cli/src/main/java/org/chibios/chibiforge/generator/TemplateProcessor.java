package org.chibios.chibiforge.generator;

import fmpp.Engine;
import fmpp.ProgressListener;
import fmpp.ProcessingException;
import org.chibios.chibiforge.container.ComponentContent;
import org.chibios.chibiforge.container.FilesystemContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

        // Resolve the cfg/ source directory
        Path cfgDir;
        if (content instanceof FilesystemContent fsContent) {
            cfgDir = fsContent.getRoot().resolve("cfg");
        } else {
            throw new UnsupportedOperationException(
                    "Template processing from non-filesystem content not yet supported");
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

        // Ensure output directory exists
        Files.createDirectories(ctx.getGeneratedRoot());

        // Configure FMPP Engine
        Engine engine = new Engine();
        engine.setSourceRoot(cfgDir.toFile());
        engine.setOutputRoot(ctx.getGeneratedRoot().toFile());

        // Strip .ftl and .ftlc extensions automatically
        engine.setRemoveFreemarkerExtensions(true);
        engine.addRemoveExtension("ftlc");

        // Inject data model variables
        engine.addData(dataModel);

        // Add progress listener to capture actions for the report
        engine.addProgressListener(new ProgressListener() {
            @Override
            public void notifyProgressEvent(
                    Engine eng, int event, File src, int pMode,
                    Throwable error, Object param) {
                if (event == ProgressListener.EVENT_END_FILE_PROCESSING && src != null) {
                    String srcRel = "cfg/" + cfgDir.relativize(src.toPath());
                    String outputRel = stripTemplateExtension(
                            cfgDir.relativize(src.toPath()).toString());
                    Path outputPath = ctx.getGeneratedRoot().resolve(outputRel);
                    log.debug("Processed template {} -> {}", srcRel, outputPath);
                    report.addAction(new GenerationAction(
                            GenerationAction.Type.TEMPLATE, srcRel,
                            outputPath.toString(), null));
                }
            }
        });

        // Process all template files
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
