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

package org.chibios.chibiforge.sources;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentSourceResolverTest {

    @Test
    void resolvesSidecarProjectAndEnvRootsInPrecedenceOrder(@TempDir Path tempDir) throws Exception {
        Path configRoot = tempDir.resolve("project");
        Files.createDirectories(configRoot);
        Path configFile = configRoot.resolve("project.xcfg");
        Files.writeString(configFile, "<dummy/>");

        Path sidecarRoot = tempDir.resolve("shared-components");
        Path projectLocalRoot = configRoot.resolve("components");
        Path envRoot = tempDir.resolve("env-components");
        Files.createDirectories(sidecarRoot);
        Files.createDirectories(projectLocalRoot);
        Files.createDirectories(envRoot);

        Files.writeString(configRoot.resolve("chibiforge_sources.json"), """
                {
                  "componentRoots": ["../shared-components"]
                }
                """);

        ComponentSourceResolver resolver = new ComponentSourceResolver(
                key -> "CHIBIFORGE_COMPONENTS".equals(key) ? envRoot.toString() : null);

        ResolvedComponentSources resolved = resolver.resolve(configFile, List.of());

        assertThat(resolved.roots()).containsExactly(
                sidecarRoot.toAbsolutePath().normalize(),
                projectLocalRoot.toAbsolutePath().normalize(),
                envRoot.toAbsolutePath().normalize());
        assertThat(resolved.warnings()).isEmpty();
    }

    @Test
    void reportsWarningsForInvalidSidecarAndEnvRoots(@TempDir Path tempDir) throws Exception {
        Path configRoot = tempDir.resolve("project");
        Files.createDirectories(configRoot);
        Path configFile = configRoot.resolve("project.xcfg");
        Files.writeString(configFile, "<dummy/>");

        Files.writeString(configRoot.resolve("chibiforge_sources.json"), """
                {
                  "componentRoots": "not-an-array"
                }
                """);

        ComponentSourceResolver resolver = new ComponentSourceResolver(
                key -> "CHIBIFORGE_COMPONENTS".equals(key)
                        ? tempDir.resolve("missing-root").toString()
                        : null);

        ResolvedComponentSources resolved = resolver.resolve(configFile, List.of());

        assertThat(resolved.roots()).isEmpty();
        assertThat(resolved.warnings()).anySatisfy(warning ->
                assertThat(warning).contains("Invalid chibiforge_sources.json"));
        assertThat(resolved.warnings()).anySatisfy(warning ->
                assertThat(warning).contains("Environment component root is invalid"));
        assertThat(resolved.warnings()).anySatisfy(warning ->
                assertThat(warning).contains("No valid component roots resolved"));
    }
}
