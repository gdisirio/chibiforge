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
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.assertj.core.api.Assertions.*;

class ComponentRegistryTest {

    private static Path componentsRoot;

    @BeforeAll
    static void setup() throws URISyntaxException {
        // The fixtures directory has a normalized sample component container.
        componentsRoot = Paths.get(
                ComponentRegistryTest.class.getResource("/fixtures").toURI());
    }

    @Test
    void discoversComponent() throws IOException {
        ComponentRegistry registry = ComponentRegistry.fromFilesystem(componentsRoot);
        // The sample fixture has schema.xml with id="org.chibios.chibiforge.components.hal.stm32f4xx"
        assertThat(registry.size()).isGreaterThanOrEqualTo(1);
        assertThat(registry.componentIds()).contains("org.chibios.chibiforge.components.hal.stm32f4xx");
    }

    @Test
    void lookupReturnsContainer() throws IOException {
        ComponentRegistry registry = ComponentRegistry.fromFilesystem(componentsRoot);
        ComponentContainer container = registry.lookup("org.chibios.chibiforge.components.hal.stm32f4xx");
        assertThat(container).isNotNull();
        assertThat(container.getId()).isEqualTo("org.chibios.chibiforge.components.hal.stm32f4xx");
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
        ComponentContainer container = registry.lookup("org.chibios.chibiforge.components.hal.stm32f4xx");
        var content = container.getComponentContent();

        assertThat(content.exists("schema.xml")).isTrue();
        assertThat(content.exists("nonexistent.xml")).isFalse();
    }

    @Test
    void filesystemContainerListsBundledPresets(@TempDir Path tempDir) throws Exception {
        createComponent(tempDir, "test.component", "Preset Fixture");
        Path presetsDir = tempDir.resolve("test.component").resolve("component").resolve("presets");
        Files.createDirectories(presetsDir.resolve("boards"));
        Files.writeString(presetsDir.resolve("default.xml"), "<preset/>", StandardCharsets.UTF_8);
        Files.writeString(presetsDir.resolve("boards/nucleo.xml"), "<preset/>", StandardCharsets.UTF_8);

        ComponentRegistry registry = ComponentRegistry.fromFilesystem(tempDir);
        ComponentContainer container = registry.lookup("test.component");

        assertThat(container.listBundledPresets())
                .containsExactly("boards/nucleo.xml", "default.xml");
        try (InputStream input = container.openBundledPreset("boards/nucleo.xml")) {
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("<preset/>");
        }
    }

    @Test
    void jarContainerListsBundledPresets(@TempDir Path tempDir) throws Exception {
        createPluginJar(tempDir.resolve("test.component_1.0.0.jar"),
                "test.component", "test.component", "1.0.0",
                List.of(
                        new JarFileEntry("component/presets/default.xml", "<preset/>"),
                        new JarFileEntry("component/presets/boards/discovery.xml", "<preset/>")));

        ComponentRegistry registry = ComponentRegistry.fromPlugins(tempDir);
        ComponentContainer container = registry.lookup("test.component");

        assertThat(container.listBundledPresets())
                .containsExactly("boards/discovery.xml", "default.xml");
        try (InputStream input = container.openBundledPreset("default.xml")) {
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("<preset/>");
        }
    }

    @Test
    void bundledPresetOpeningRejectsPathTraversal(@TempDir Path tempDir) throws Exception {
        createComponent(tempDir, "test.component", "Preset Fixture");

        ComponentRegistry registry = ComponentRegistry.fromFilesystem(tempDir);
        ComponentContainer container = registry.lookup("test.component");

        assertThatThrownBy(() -> container.openBundledPreset("../secret.xml"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("component/presets/");
    }

    @Test
    void orderedRootsUseHigherPrecedenceFirst(@TempDir Path tempDir) throws Exception {
        Path lowRoot = tempDir.resolve("low-root");
        Path highRoot = tempDir.resolve("high-root");
        createComponent(lowRoot, "test.component", "Low Precedence");
        createComponent(highRoot, "test.component", "High Precedence");

        ComponentRegistry registry = ComponentRegistry.build(List.of(highRoot, lowRoot));

        ComponentContainer container = registry.lookup("test.component");
        assertThat(container.loadDefinition().getName()).isEqualTo("High Precedence");
    }

    @Test
    void supportsExactVersionLookupAndLaterVersionDiscovery(@TempDir Path tempDir) throws Exception {
        createPluginJar(tempDir.resolve("test.component_1.0.0.jar"), "test.component", "test.component", "1.0.0");
        createPluginJar(tempDir.resolve("test.component_1.2.0.jar"), "test.component", "test.component", "1.2.0");

        ComponentRegistry registry = ComponentRegistry.fromPlugins(tempDir);

        assertThat(registry.lookup("test.component").loadDefinition().getVersion()).isEqualTo("1.2.0");
        assertThat(registry.lookup("test.component", "1.0.0").loadDefinition().getVersion()).isEqualTo("1.0.0");
        assertThat(registry.lookup("test.component", "1.2.0").loadDefinition().getVersion()).isEqualTo("1.2.0");
        assertThat(registry.availableVersions("test.component")).containsExactly("1.0.0", "1.2.0");
        assertThat(registry.findLatestLaterVersion("test.component", "1.0.0"))
                .isPresent()
                .get()
                .extracting(container -> {
                    try {
                        return container.loadDefinition().getVersion();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .isEqualTo("1.2.0");
        assertThat(registry.findLatestLaterVersion("test.component", "1.2.0")).isEmpty();
    }

    @Test
    void rejectsFilesystemComponentWithMismatchedDirectoryName(@TempDir Path tempDir) throws IOException {
        createComponentInDirectory(tempDir, "wrong.name", "test.component", "Invalid");

        assertThatThrownBy(() -> ComponentRegistry.fromFilesystem(tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Filesystem component identity mismatch: directory name 'wrong.name' does not match schema.xml id 'test.component'");
    }

    @Test
    void rejectsPluginJarWithMismatchedFilenamePrefix(@TempDir Path tempDir) throws Exception {
        createPluginJar(tempDir.resolve("wrong.name_1.0.0.jar"), "test.component", "test.component", "1.0.0");

        assertThatThrownBy(() -> ComponentRegistry.fromPlugins(tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Plugin component identity mismatch: JAR name 'wrong.name_1.0.0.jar' does not match expected 'test.component_1.0.0.jar'");
    }

    @Test
    void rejectsPluginJarWithMismatchedBundleSymbolicName(@TempDir Path tempDir) throws Exception {
        createPluginJar(tempDir.resolve("test.component_1.0.0.jar"), "other.component", "test.component", "1.0.0");

        assertThatThrownBy(() -> ComponentRegistry.fromPlugins(tempDir))
                .isInstanceOf(IllegalArgumentException.class)
                .hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasRootCauseMessage("Plugin component identity mismatch: Bundle-SymbolicName 'other.component' does not match schema.xml id 'test.component'");
    }

    private void createComponent(Path root, String componentId, String componentName) throws IOException {
        createComponentInDirectory(root, componentId, componentId, componentName);
    }

    private void createComponentInDirectory(Path root, String directoryName, String componentId, String componentName) throws IOException {
        Path componentDir = root.resolve(directoryName).resolve("component");
        Files.createDirectories(componentDir);
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <component xmlns="http://chibiforge/schema/component"
                           id="%s"
                           name="%s"
                           version="1.0.0"
                           hidden="false"
                           is_platform="false">
                  <description>Test.</description>
                  <resources/>
                  <categories>
                    <category id="Test"/>
                  </categories>
                  <requires/>
                  <provides/>
                  <sections>
                    <section name="Settings" expanded="true" editable="true" visible="true">
                      <description>Section.</description>
                    </section>
                  </sections>
                </component>
                """.formatted(componentId, componentName);
        Files.writeString(componentDir.resolve("schema.xml"), xml, StandardCharsets.UTF_8);
    }

    private void createPluginJar(Path jarPath, String bundleSymbolicName, String componentId, String version) throws IOException {
        createPluginJar(jarPath, bundleSymbolicName, componentId, version, List.of());
    }

    private void createPluginJar(Path jarPath, String bundleSymbolicName, String componentId,
                                 String version, List<JarFileEntry> extraEntries) throws IOException {
        Files.createDirectories(jarPath.getParent());
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Bundle-SymbolicName", bundleSymbolicName);

        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath), manifest)) {
            addEntry(jar, "plugin.xml", """
                    <plugin>
                      <extension point="org.chibios.chibiforge.component"/>
                    </plugin>
                    """);
            addEntry(jar, "component/schema.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <component
                        xmlns="http://chibiforge/schema/component"
                        id="%s"
                        name="Plugin Component"
                        version="%s"
                        hidden="false"
                        is_platform="false">
                      <description>Plugin test.</description>
                      <resources/>
                      <categories><category id="Test"/></categories>
                      <requires/>
                      <provides/>
                      <sections>
                        <section name="Settings" expanded="true" editable="true" visible="true">
                          <description>Section.</description>
                        </section>
                      </sections>
                    </component>
                    """.formatted(componentId, version));
            for (JarFileEntry entry : extraEntries) {
                addEntry(jar, entry.path(), entry.content());
            }
        }
    }

    private record JarFileEntry(String path, String content) {
    }

    private void addEntry(JarOutputStream jar, String name, String content) throws IOException {
        jar.putNextEntry(new JarEntry(name));
        jar.write(content.getBytes(StandardCharsets.UTF_8));
        jar.closeEntry();
    }
}
