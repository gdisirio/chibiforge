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

package org.chibios.chibiforge.registry;

import org.chibios.chibiforge.container.ComponentContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.*;

class ComponentRegistryTest {

    private static Path componentsRoot;

    @BeforeAll
    static void setup() throws URISyntaxException {
        // The fixtures directory has simple-component/ which contains component/schema.xml
        componentsRoot = Paths.get(
                ComponentRegistryTest.class.getResource("/fixtures").toURI());
    }

    @Test
    void discoversComponent() throws IOException {
        ComponentRegistry registry = ComponentRegistry.fromFilesystem(componentsRoot);
        // simple-component has schema.xml with id="org.chibios.hal.stm32f4xx"
        assertThat(registry.size()).isGreaterThanOrEqualTo(1);
        assertThat(registry.componentIds()).contains("org.chibios.hal.stm32f4xx");
    }

    @Test
    void lookupReturnsContainer() throws IOException {
        ComponentRegistry registry = ComponentRegistry.fromFilesystem(componentsRoot);
        ComponentContainer container = registry.lookup("org.chibios.hal.stm32f4xx");
        assertThat(container).isNotNull();
        assertThat(container.getId()).isEqualTo("org.chibios.hal.stm32f4xx");
    }

    @Test
    void lookupThrowsForMissing() throws IOException {
        ComponentRegistry registry = ComponentRegistry.fromFilesystem(componentsRoot);
        assertThatThrownBy(() -> registry.lookup("nonexistent"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    void skipsDirectoriesWithoutSchema(@TempDir Path tempDir) throws IOException {
        Path noSchema = tempDir.resolve("not-a-component");
        Files.createDirectories(noSchema);
        Files.writeString(noSchema.resolve("readme.txt"), "nothing here");

        ComponentRegistry registry = ComponentRegistry.fromFilesystem(tempDir);
        assertThat(registry.size()).isEqualTo(0);
    }

    @Test
    void contentListsFiles() throws IOException {
        ComponentRegistry registry = ComponentRegistry.fromFilesystem(componentsRoot);
        ComponentContainer container = registry.lookup("org.chibios.hal.stm32f4xx");
        var content = container.getComponentContent();

        assertThat(content.exists("schema.xml")).isTrue();
        assertThat(content.exists("nonexistent.xml")).isFalse();
    }
}
