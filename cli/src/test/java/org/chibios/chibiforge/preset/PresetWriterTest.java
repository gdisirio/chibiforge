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

package org.chibios.chibiforge.preset;

import org.chibios.chibiforge.component.ComponentDefinition;
import org.chibios.chibiforge.component.ComponentDefinitionParser;
import org.chibios.chibiforge.config.ComponentConfigEntry;
import org.chibios.chibiforge.config.ConfigLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PresetWriterTest {

    private static final String COMPONENT_ID = "org.chibios.chibiforge.components.hal.stm32f4xx";

    private static ComponentDefinition definition;

    private final ConfigLoader configLoader = new ConfigLoader();
    private final PresetLoader presetLoader = new PresetLoader();
    private final PresetApplier presetApplier = new PresetApplier();
    private final PresetWriter presetWriter = new PresetWriter();

    @BeforeAll
    static void loadFixtureDefinition() throws Exception {
        ComponentDefinitionParser parser = new ComponentDefinitionParser();
        try (InputStream is = PresetWriterTest.class.getResourceAsStream(
                "/fixtures/org.chibios.chibiforge.components.hal.stm32f4xx/component/schema.xml")) {
            definition = parser.parse(is);
        }
    }

    @Test
    void exportsTargetResolvedPresetAndRoundTripsThroughLoaderAndApplier(@TempDir Path tempDir) throws Exception {
        Element component = loadComponent("""
                <?xml version="1.0" encoding="UTF-8"?>
                <chibiforgeConfiguration xmlns="http://chibiforge/schema/config" toolVersion="1.0.0" schemaVersion="1.0">
                  <components>
                    <component id="org.chibios.chibiforge.components.hal.stm32f4xx" version="1.0.0">
                      <initialization_settings>
                        <do_not_init>false</do_not_init>
                        <vdd>300</vdd>
                        <hse_frequency>12000000</hse_frequency>
                        <lse_frequency>32768</lse_frequency>
                        <system_clock_source>HSE</system_clock_source>
                        <board_name default="BASE_NAME">
                          <targetValue target="debug">DBG_NAME</targetValue>
                        </board_name>
                        <notes>Debug notes</notes>
                      </initialization_settings>
                      <pin_settings>
                        <pins>
                          <item>
                            <pin_details>
                              <name>PA2</name>
                              <mode>OUTPUT</mode>
                              <speed>HIGH</speed>
                            </pin_details>
                          </item>
                        </pins>
                      </pin_settings>
                    </component>
                  </components>
                </chibiforgeConfiguration>
                """);

        Path presetPath = tempDir.resolve("debug-preset.xml");
        presetWriter.save("Debug Preset", definition, component, "debug", presetPath);

        PresetDefinition preset = presetLoader.load(presetPath);
        assertThat(preset.name()).isEqualTo("Debug Preset");
        assertThat(preset.componentId()).isEqualTo(COMPONENT_ID);

        PresetSection initialization = preset.sections().get(0);
        assertThat(findProperty(initialization, "board_name").value()).isEqualTo("DBG_NAME");
        assertThat(findProperty(initialization, "hse_frequency").value()).isEqualTo("12000000");

        PresetSection pinSettings = preset.sections().get(1);
        PresetProperty pins = findProperty(pinSettings, "pins");
        assertThat(pins.items()).hasSize(1);
        PresetSection pinDetails = pins.items().get(0).sections().get(0);
        assertThat(findProperty(pinDetails, "name").value()).isEqualTo("PA2");
        assertThat(findProperty(pinDetails, "mode").value()).isEqualTo("OUTPUT");
        assertThat(findProperty(pinDetails, "speed").value()).isEqualTo("HIGH");

        Element cleanComponent = loadComponent(baseConfigXml());
        presetApplier.apply(preset, definition, cleanComponent);

        Element init = directChild(cleanComponent, "initialization_settings");
        assertThat(textOf(init, "board_name")).isEqualTo("DBG_NAME");
        assertThat(textOf(init, "hse_frequency")).isEqualTo("12000000");
        assertThat(textOf(init, "system_clock_source")).isEqualTo("HSE");

        Element list = directChild(directChild(cleanComponent, "pin_settings"), "pins");
        assertThat(elementChildren(list)).hasSize(1);
        Element item = elementChildren(list).get(0);
        Element details = directChild(item, "pin_details");
        assertThat(textOf(details, "name")).isEqualTo("PA2");
        assertThat(textOf(details, "mode")).isEqualTo("OUTPUT");
        assertThat(textOf(details, "speed")).isEqualTo("HIGH");
    }

    @Test
    void exportsSchemaDefaultsForMissingScalarValues() throws Exception {
        Element component = loadComponent("""
                <?xml version="1.0" encoding="UTF-8"?>
                <chibiforgeConfiguration xmlns="http://chibiforge/schema/config" toolVersion="1.0.0" schemaVersion="1.0">
                  <components>
                    <component id="org.chibios.chibiforge.components.hal.stm32f4xx" version="1.0.0">
                      <initialization_settings>
                        <do_not_init>false</do_not_init>
                        <vdd>300</vdd>
                      </initialization_settings>
                    </component>
                  </components>
                </chibiforgeConfiguration>
                """);

        Document presetDoc = presetWriter.export("Defaults", definition, component, "default");
        PresetDefinition preset = presetLoader.load(presetDoc);

        PresetSection initialization = preset.sections().get(0);
        assertThat(findProperty(initialization, "hse_frequency").value()).isEqualTo("8000000");
        assertThat(findProperty(initialization, "board_name").value()).isEqualTo("STM32F4_BOARD");
        assertThat(findProperty(initialization, "notes").value()).isEmpty();
    }

    @Test
    void exportsEmptyListAsEmptyItems() throws Exception {
        Element component = loadComponent(baseConfigXml());

        Document presetDoc = presetWriter.export("Empty Pins", definition, component, "default");
        PresetDefinition preset = presetLoader.load(presetDoc);

        PresetProperty pins = findProperty(preset.sections().get(1), "pins");
        assertThat(pins.items()).isEmpty();
    }

    @Test
    void rejectsUnsupportedMultiTargetListExport() throws Exception {
        Element component = loadComponent("""
                <?xml version="1.0" encoding="UTF-8"?>
                <chibiforgeConfiguration xmlns="http://chibiforge/schema/config" toolVersion="1.0.0" schemaVersion="1.0">
                  <components>
                    <component id="org.chibios.chibiforge.components.hal.stm32f4xx" version="1.0.0">
                      <initialization_settings>
                        <do_not_init>false</do_not_init>
                        <vdd>300</vdd>
                        <hse_frequency>8000000</hse_frequency>
                        <lse_frequency>32768</lse_frequency>
                        <system_clock_source>PLL</system_clock_source>
                        <board_name>STM32F4_BOARD</board_name>
                        <notes></notes>
                      </initialization_settings>
                      <pin_settings>
                        <pins default="">
                          <targetValue target="debug"/>
                        </pins>
                      </pin_settings>
                    </component>
                  </components>
                </chibiforgeConfiguration>
                """);

        assertThatThrownBy(() -> presetWriter.export("Bad", definition, component, "debug"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported target-specific encoding");
    }

    private Element loadComponent(String xml) throws Exception {
        ConfigLoader.LoadedConfiguration loaded = configLoader.loadWithDocument(
                new ByteArrayInputStream(xml.stripLeading().getBytes(StandardCharsets.UTF_8)));
        ComponentConfigEntry entry = loaded.configuration().getComponents().get(0);
        return entry.getConfigElement();
    }

    private String baseConfigXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <chibiforgeConfiguration xmlns="http://chibiforge/schema/config" toolVersion="1.0.0" schemaVersion="1.0">
                  <components>
                    <component id="org.chibios.chibiforge.components.hal.stm32f4xx" version="1.0.0">
                      <initialization_settings>
                        <do_not_init>false</do_not_init>
                        <vdd>300</vdd>
                        <hse_frequency>8000000</hse_frequency>
                        <lse_frequency>32768</lse_frequency>
                        <system_clock_source>PLL</system_clock_source>
                        <board_name>STM32F4_BOARD</board_name>
                        <notes>Old notes</notes>
                      </initialization_settings>
                      <pin_settings>
                        <pins/>
                      </pin_settings>
                    </component>
                  </components>
                </chibiforgeConfiguration>
                """;
    }

    private PresetProperty findProperty(PresetSection section, String name) {
        return section.properties().stream()
                .filter(property -> property.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing preset property '" + name + "'"));
    }

    private String textOf(Element parent, String... path) {
        Element current = parent;
        for (String name : path) {
            current = directChild(current, name);
        }
        return current.getTextContent().trim();
    }

    private Element directChild(Element parent, String name) {
        for (int i = 0; i < parent.getChildNodes().getLength(); i++) {
            if (parent.getChildNodes().item(i) instanceof Element child
                    && (name.equals(child.getTagName()) || name.equals(child.getLocalName()))) {
                return child;
            }
        }
        throw new IllegalArgumentException("Missing child '" + name + "' under <" + parent.getTagName() + ">");
    }

    private List<Element> elementChildren(Element parent) {
        List<Element> children = new ArrayList<>();
        for (int i = 0; i < parent.getChildNodes().getLength(); i++) {
            if (parent.getChildNodes().item(i) instanceof Element child) {
                children.add(child);
            }
        }
        return children;
    }
}
