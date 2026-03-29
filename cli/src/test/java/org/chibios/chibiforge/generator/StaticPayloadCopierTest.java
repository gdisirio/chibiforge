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

import org.chibios.chibiforge.container.FilesystemContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class StaticPayloadCopierTest {

    private FilesystemContent content;
    private StaticPayloadCopier copier;

    @BeforeEach
    void setup() throws Exception {
        Path componentDir = Paths.get(
                getClass().getResource("/fixtures/simple-component/component").toURI());
        content = new FilesystemContent(componentDir);
        copier = new StaticPayloadCopier();
    }

    @Test
    void copiesSourceToGenerated(@TempDir Path tempDir) throws Exception {
        GenerationContext ctx = new GenerationContext(tempDir.resolve("chibiforge.xcfg"), tempDir, "default", false, false);
        GenerationReport report = new GenerationReport();

        copier.copyPayloads("org.chibios.hal.stm32f4xx", content, ctx, report);

        Path expected = tempDir.resolve("generated/org_chibios_hal_stm32f4xx/static_file.c");
        assertThat(expected).exists();
        assertThat(Files.readString(expected)).contains("hal_init");
    }

    @Test
    void copiesSourceRootWa(@TempDir Path tempDir) throws Exception {
        GenerationContext ctx = new GenerationContext(tempDir.resolve("chibiforge.xcfg"), tempDir, "default", false, false);
        GenerationReport report = new GenerationReport();

        copier.copyPayloads("org.chibios.hal.stm32f4xx", content, ctx, report);

        Path expected = tempDir.resolve("always.txt");
        assertThat(expected).exists();
        assertThat(Files.readString(expected)).contains("always overwritten");
    }

    @Test
    void copiesSourceRootWo(@TempDir Path tempDir) throws Exception {
        GenerationContext ctx = new GenerationContext(tempDir.resolve("chibiforge.xcfg"), tempDir, "default", false, false);
        GenerationReport report = new GenerationReport();

        copier.copyPayloads("org.chibios.hal.stm32f4xx", content, ctx, report);

        Path expected = tempDir.resolve("once.txt");
        assertThat(expected).exists();
        assertThat(Files.readString(expected)).contains("write-once");
    }

    @Test
    void writeOnceDoesNotOverwrite(@TempDir Path tempDir) throws Exception {
        // Pre-create the file with different content
        Path existing = tempDir.resolve("once.txt");
        Files.writeString(existing, "user modified");

        GenerationContext ctx = new GenerationContext(tempDir.resolve("chibiforge.xcfg"), tempDir, "default", false, false);
        GenerationReport report = new GenerationReport();

        copier.copyPayloads("org.chibios.hal.stm32f4xx", content, ctx, report);

        assertThat(Files.readString(existing)).isEqualTo("user modified");
        assertThat(report.countByType(GenerationAction.Type.SKIP)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void dryRunDoesNotWrite(@TempDir Path tempDir) throws Exception {
        GenerationContext ctx = new GenerationContext(tempDir.resolve("chibiforge.xcfg"), tempDir, "default", true, false);
        GenerationReport report = new GenerationReport();

        copier.copyPayloads("org.chibios.hal.stm32f4xx", content, ctx, report);

        // No files should be created
        assertThat(tempDir.resolve("generated")).doesNotExist();
        assertThat(tempDir.resolve("always.txt")).doesNotExist();
        assertThat(tempDir.resolve("once.txt")).doesNotExist();

        // But actions should be recorded
        assertThat(report.getActions()).isNotEmpty();
    }
}
