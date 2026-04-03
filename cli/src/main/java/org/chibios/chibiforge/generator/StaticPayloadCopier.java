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

import org.chibios.chibiforge.container.ComponentContent;
import org.chibios.chibiforge.datamodel.IdNormalizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Copies static payload files from component containers to the configuration root.
 *
 * source/            -> generated/<normalizedId>/  (always overwrite)
 * build/             -> generated/<normalizedId>/build/ (always overwrite)
 * source_root_wa/    -> configRoot/                (always overwrite)
 * source_root_wo/    -> configRoot/                (write-once)
 * any other unknown directory under component/ is copied statically to
 * generated/<normalizedId>/<dir>/ (always overwrite)
 */
public class StaticPayloadCopier {

    private static final Logger log = LoggerFactory.getLogger(StaticPayloadCopier.class);
    private static final Set<String> RESERVED_TOP_LEVEL_DIRS = Set.of(
            "cfg",
            "cfg_root_wa",
            "cfg_root_wo",
            "source",
            "source_root_wa",
            "source_root_wo",
            "resources",
            "rsc",
            "presets"
    );

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

        copyGenericGeneratedDirectories(content,
                ctx.getGeneratedRoot().resolve(normalizedId),
                ctx, report);
    }

    private void copyGenericGeneratedDirectories(ComponentContent content, Path generatedComponentRoot,
                                                 GenerationContext ctx, GenerationReport report) throws IOException {
        for (String dirName : discoverGenericGeneratedDirectories(content)) {
            copyDirectory(content, dirName + "/",
                    generatedComponentRoot.resolve(dirName),
                    dirName + "/", true, ctx, report);
        }
    }

    private List<String> discoverGenericGeneratedDirectories(ComponentContent content) throws IOException {
        Set<String> result = new LinkedHashSet<>();
        for (String relativePath : content.list("")) {
            int slash = relativePath.indexOf('/');
            if (slash <= 0) {
                continue;
            }
            String topLevelDir = relativePath.substring(0, slash);
            if (!RESERVED_TOP_LEVEL_DIRS.contains(topLevelDir)) {
                result.add(topLevelDir);
            }
        }
        return result.stream().sorted(Comparator.naturalOrder()).toList();
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
