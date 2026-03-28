package org.chibios.chibiforge.generator;

import freemarker.template.Configuration;
import freemarker.template.Template;
import org.chibios.chibiforge.container.ComponentContent;
import org.chibios.chibiforge.container.FilesystemContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Processes FreeMarker templates from component cfg/ directory.
 *
 * Phase 1: Uses FreeMarker directly (not FMPP). Supports .ftl (classic mode) templates.
 * .ftlc (code-first) support requires freemarker-codegen and will be added later.
 *
 * Output mapping: cfg/foo.h.ftl -> generated/foo.h (strip template extension).
 * Directory structure under cfg/ is preserved in the output.
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

        // Configure FreeMarker
        Configuration fmConfig = new Configuration(Configuration.VERSION_2_3_33);
        fmConfig.setDefaultEncoding("UTF-8");

        // Set template source directory
        if (content instanceof FilesystemContent fsContent) {
            fmConfig.setDirectoryForTemplateLoading(fsContent.getRoot().resolve("cfg").toFile());
        } else {
            throw new UnsupportedOperationException(
                    "Template processing from non-filesystem content not yet supported");
        }

        for (String relativePath : templateFiles) {
            // relativePath is e.g. "cfg/mcuconf.h.ftl"
            String templateRelPath = relativePath.substring("cfg/".length()); // e.g. "mcuconf.h.ftl"
            String outputRelPath = stripTemplateExtension(templateRelPath);

            if (outputRelPath == null) {
                log.debug("Skipping non-template file: {}", relativePath);
                continue;
            }

            Path outputPath = ctx.getGeneratedRoot().resolve(outputRelPath);

            if (ctx.isDryRun()) {
                log.info("[dry-run] Would process template {} -> {}", relativePath, outputPath);
                report.addAction(new GenerationAction(
                        GenerationAction.Type.TEMPLATE, relativePath, outputPath.toString(), "dry-run"));
                continue;
            }

            Files.createDirectories(outputPath.getParent());

            Template template = fmConfig.getTemplate(templateRelPath);
            try (Writer out = new BufferedWriter(new FileWriter(outputPath.toFile()))) {
                template.process(dataModel, out);
            }

            log.debug("Processed template {} -> {}", relativePath, outputPath);
            report.addAction(new GenerationAction(
                    GenerationAction.Type.TEMPLATE, relativePath, outputPath.toString(), null));
        }
    }

    /**
     * Strip the template extension (.ftl or .ftlc) from the filename.
     * Returns null if the file is not a recognized template.
     */
    private String stripTemplateExtension(String path) {
        if (path.endsWith(".ftl")) {
            return path.substring(0, path.length() - ".ftl".length());
        }
        if (path.endsWith(".ftlc")) {
            return path.substring(0, path.length() - ".ftlc".length());
        }
        return null;
    }
}
