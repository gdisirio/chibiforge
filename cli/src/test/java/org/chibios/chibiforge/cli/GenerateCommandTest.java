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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class GenerateCommandTest {

    @Test
    void autoDiscoversProjectLocalComponents(@TempDir Path tempDir) throws Exception {
        Path projectDir = tempDir.resolve("project");
        Files.createDirectories(projectDir);
        Path configFile = projectDir.resolve("chibiforge.xcfg");
        Files.copy(fixturePath("configs/simple.xcfg"), configFile);

        Path componentDir = projectDir.resolve("components")
                .resolve("org.chibios.chibiforge.components.hal.stm32f4xx");
        copyDirectory(fixturePath("org.chibios.chibiforge.components.hal.stm32f4xx"), componentDir);

        int exitCode = new CommandLine(new GenerateCommand())
                .execute("--project", projectDir.toString(), "--dry-run");

        assertThat(exitCode).isZero();
    }

    @Test
    void autoDiscoversSidecarComponentRoots(@TempDir Path tempDir) throws Exception {
        Path projectDir = tempDir.resolve("project");
        Files.createDirectories(projectDir);
        Path configFile = projectDir.resolve("chibiforge.xcfg");
        Files.copy(fixturePath("configs/simple.xcfg"), configFile);

        Path externalRoot = tempDir.resolve("external-components");
        Path componentDir = externalRoot.resolve("org.chibios.chibiforge.components.hal.stm32f4xx");
        copyDirectory(fixturePath("org.chibios.chibiforge.components.hal.stm32f4xx"), componentDir);

        Files.writeString(projectDir.resolve("chibiforge_sources.json"), """
                {
                  "componentRoots": ["../external-components"]
                }
                """);

        int exitCode = new CommandLine(new GenerateCommand())
                .execute("--project", projectDir.toString(), "--dry-run");

        assertThat(exitCode).isZero();
    }

    private static Path fixturePath(String relativePath) {
        return Path.of("src", "test", "resources", "fixtures").resolve(relativePath);
    }

    private static void copyDirectory(Path source, Path destination) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            stream.sorted(Comparator.naturalOrder()).forEach(path -> {
                try {
                    Path target = destination.resolve(source.relativize(path).toString());
                    if (Files.isDirectory(path)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(path, target);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }
}
