package org.chibios.chibiforge.generator;

import org.chibios.chibiforge.container.ComponentContent;
import org.chibios.chibiforge.datamodel.IdNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Copies static payload files from component containers to the configuration root.
 *
 * source/            -> generated/<normalizedId>/  (always overwrite)
 * source_root_wa/    -> configRoot/                (always overwrite)
 * source_root_wo/    -> configRoot/                (write-once)
 */
public class StaticPayloadCopier {

    private static final Logger log = LoggerFactory.getLogger(StaticPayloadCopier.class);

    public void copyPayloads(String componentId, ComponentContent content,
                             GenerationContext ctx, GenerationReport report) throws IOException {
        String normalizedId = IdNormalizer.normalize(componentId);

        // source/ -> generated/<normalizedId>/
        copyDirectory(content, "source/",
                ctx.getGeneratedRoot().resolve(normalizedId),
                "source/", true, ctx, report);

        // source_root_wa/ -> configRoot/ (always)
        copyDirectory(content, "source_root_wa/",
                ctx.getConfigRoot(),
                "source_root_wa/", true, ctx, report);

        // source_root_wo/ -> configRoot/ (write-once)
        copyDirectory(content, "source_root_wo/",
                ctx.getConfigRoot(),
                "source_root_wo/", false, ctx, report);
    }

    private void copyDirectory(ComponentContent content, String prefix, Path destRoot,
                               String displayPrefix, boolean overwrite,
                               GenerationContext ctx, GenerationReport report) throws IOException {
        List<String> files = content.list(prefix);
        for (String relativePath : files) {
            // relativePath is relative to component/ e.g. "source/hal_ll.c"
            // Strip the prefix to get the file's relative path within the source dir
            String withinPrefix = relativePath.substring(prefix.length());
            Path destPath = destRoot.resolve(withinPrefix);

            if (!overwrite && Files.exists(destPath)) {
                log.debug("Skipping (write-once): {}", destPath);
                report.addAction(new GenerationAction(
                        GenerationAction.Type.SKIP, relativePath, destPath.toString(), "write-once, exists"));
                continue;
            }

            if (ctx.isDryRun()) {
                log.info("[dry-run] Would copy {} -> {}", relativePath, destPath);
                report.addAction(new GenerationAction(
                        GenerationAction.Type.COPY, relativePath, destPath.toString(), "dry-run"));
            } else {
                Files.createDirectories(destPath.getParent());
                try (InputStream is = content.open(relativePath)) {
                    Files.copy(is, destPath, StandardCopyOption.REPLACE_EXISTING);
                }
                log.debug("Copied {} -> {}", relativePath, destPath);
                report.addAction(new GenerationAction(
                        GenerationAction.Type.COPY, relativePath, destPath.toString(), null));
            }
        }
    }
}
