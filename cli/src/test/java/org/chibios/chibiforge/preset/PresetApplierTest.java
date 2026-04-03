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
import org.w3c.dom.Element;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PresetApplierTest {

    private static final String COMPONENT_ID = "org.chibios.chibiforge.components.hal.stm32f4xx";

    private static ComponentDefinition definition;

    private final PresetLoader presetLoader = new PresetLoader();
    private final PresetApplier applier = new PresetApplier();
    private final ConfigLoader configLoader = new ConfigLoader();

    @BeforeAll
    static void loadFixtureDefinition() throws Exception {
        ComponentDefinitionParser parser = new ComponentDefinitionParser();
        try (InputStream is = PresetApplierTest.class.getResourceAsStream(
                "/fixtures/org.chibios.chibiforge.components.hal.stm32f4xx/component/schema.xml")) {
            definition = parser.parse(is);
        }
    }

    @Test
    void appliesOnlyExplicitScalarProperties() throws Exception {
        Element component = loadComponent("""
                <?xml version="1.0" encoding="UTF-8"?>
                <chibiforgeConfiguration xmlns="http://chibiforge/schema/config" toolVersion="1.0.0" schemaVersion="1.0">
                  <components>
                    <component id="org.chibios.chibiforge.components.hal.stm32f4xx">
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
                """);

        PresetDefinition preset = loadPreset("""
                <?xml version="1.0" encoding="UTF-8"?>
                <preset xmlns="http://www.example.org/chibiforge_preset/"
                        name="Scalar Patch"
                        id="org.chibios.chibiforge.components.hal.stm32f4xx"
                        version="1.0.0">
                  <sections>
                    <section name="Initialization Settings">
                      <property name="hse_frequency" type="int">
                        <value>12000000</value>
                      </property>
                      <property name="notes" type="text">
                        <value>Updated notes</value>
                      </property>
                    </section>
                  </sections>
                </preset>
                """);

        PresetApplyReport report = applier.apply(preset, definition, component);

        assertThat(report.updatedCount()).isEqualTo(2);
        assertThat(report.ignoredCount()).isZero();
        assertThat(report.unchangedCount()).isZero();
        assertThat(textOf(component, "initialization_settings", "hse_frequency")).isEqualTo("12000000");
        assertThat(textOf(component, "initialization_settings", "notes")).isEqualTo("Updated notes");
        assertThat(textOf(component, "initialization_settings", "board_name")).isEqualTo("STM32F4_BOARD");
    }

    @Test
    void ignoresUnknownPathsWithWarning() throws Exception {
        Element component = loadComponent(baseConfigXml());
        PresetDefinition preset = loadPreset("""
                <?xml version="1.0" encoding="UTF-8"?>
                <preset xmlns="http://www.example.org/chibiforge_preset/"
                        name="Unknown Path"
                        id="org.chibios.chibiforge.components.hal.stm32f4xx"
                        version="1.0.0">
                  <sections>
                    <section name="Initialization Settings">
                      <property name="pll_old_mode" type="enum">
                        <value>PLL</value>
                      </property>
                    </section>
                  </sections>
                </preset>
                """);

        PresetApplyReport report = applier.apply(preset, definition, component);

        assertThat(report.updatedCount()).isZero();
        assertThat(report.ignoredCount()).isEqualTo(1);
        assertThat(report.warnings()).singleElement().satisfies(warning -> {
            assertThat(warning).contains("Initialization Settings / pll_old_mode");
            assertThat(warning).contains("path not present");
        });
        assertThat(textOf(component, "initialization_settings", "board_name")).isEqualTo("STM32F4_BOARD");
    }

    @Test
    void abortsOnComponentIdMismatch() throws Exception {
        Element component = loadComponent(baseConfigXml());
        PresetDefinition preset = loadPreset("""
                <?xml version="1.0" encoding="UTF-8"?>
                <preset xmlns="http://www.example.org/chibiforge_preset/"
                        name="Wrong Component"
                        id="org.chibios.chibiforge.components.board.stm32f4xx"
                        version="1.0.0">
                  <sections>
                    <section name="Initialization Settings">
                      <property name="board_name" type="string">
                        <value>WRONG</value>
                      </property>
                    </section>
                  </sections>
                </preset>
                """);

        assertThatThrownBy(() -> applier.apply(preset, definition, component))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Preset id");
    }

    @Test
    void abortsOnInvalidScalarValue() throws Exception {
        Element component = loadComponent(baseConfigXml());
        PresetDefinition preset = loadPreset("""
                <?xml version="1.0" encoding="UTF-8"?>
                <preset xmlns="http://www.example.org/chibiforge_preset/"
                        name="Invalid Int"
                        id="org.chibios.chibiforge.components.hal.stm32f4xx"
                        version="1.0.0">
                  <sections>
                    <section name="Initialization Settings">
                      <property name="vdd" type="int">
                        <value>999</value>
                      </property>
                    </section>
                  </sections>
                </preset>
                """);

        assertThatThrownBy(() -> applier.apply(preset, definition, component))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("above int_max");
    }

    @Test
    void appliesToDefaultWhenTargetOverrideDoesNotExist() throws Exception {
        Element component = loadComponent("""
                <?xml version="1.0" encoding="UTF-8"?>
                <chibiforgeConfiguration xmlns="http://chibiforge/schema/config" toolVersion="1.0.0" schemaVersion="1.0">
                  <components>
                    <component id="org.chibios.chibiforge.components.hal.stm32f4xx">
                      <initialization_settings>
                        <board_name default="BASE_NAME">
                          <targetValue target="debug">DBG_NAME</targetValue>
                        </board_name>
                      </initialization_settings>
                    </component>
                  </components>
                </chibiforgeConfiguration>
                """);
        PresetDefinition preset = loadPreset("""
                <?xml version="1.0" encoding="UTF-8"?>
                <preset xmlns="http://www.example.org/chibiforge_preset/"
                        name="Release Name"
                        id="org.chibios.chibiforge.components.hal.stm32f4xx"
                        version="1.0.0">
                  <sections>
                    <section name="Initialization Settings">
                      <property name="board_name" type="string">
                        <value>REL_NAME</value>
                      </property>
                    </section>
                  </sections>
                </preset>
                """);

        PresetApplyReport report = applier.apply(preset, definition, component,
                new PresetApplier.ApplyOptions("release", false));

        Element boardName = directChild(directChild(component, "initialization_settings"), "board_name");
        assertThat(report.updatedCount()).isEqualTo(1);
        assertThat(boardName.getAttribute("default")).isEqualTo("REL_NAME");
        assertThat(textOf(boardName, "targetValue")).isEqualTo("DBG_NAME");
    }

    @Test
    void createsExplicitTargetOverrideWhenRequested() throws Exception {
        Element component = loadComponent("""
                <?xml version="1.0" encoding="UTF-8"?>
                <chibiforgeConfiguration xmlns="http://chibiforge/schema/config" toolVersion="1.0.0" schemaVersion="1.0">
                  <components>
                    <component id="org.chibios.chibiforge.components.hal.stm32f4xx">
                      <initialization_settings>
                        <board_name default="BASE_NAME"/>
                      </initialization_settings>
                    </component>
                  </components>
                </chibiforgeConfiguration>
                """);
        PresetDefinition preset = loadPreset("""
                <?xml version="1.0" encoding="UTF-8"?>
                <preset xmlns="http://www.example.org/chibiforge_preset/"
                        name="Debug Name"
                        id="org.chibios.chibiforge.components.hal.stm32f4xx"
                        version="1.0.0">
                  <sections>
                    <section name="Initialization Settings">
                      <property name="board_name" type="string">
                        <value>DBG_NAME</value>
                      </property>
                    </section>
                  </sections>
                </preset>
                """);

        applier.apply(preset, definition, component, new PresetApplier.ApplyOptions("debug", true));

        Element boardName = directChild(directChild(component, "initialization_settings"), "board_name");
        assertThat(boardName.getAttribute("default")).isEqualTo("BASE_NAME");
        Element targetValue = directChild(boardName, "targetValue");
        assertThat(targetValue.getAttribute("target")).isEqualTo("debug");
        assertThat(targetValue.getTextContent().trim()).isEqualTo("DBG_NAME");
    }

    @Test
    void replacesListPropertyUsingStructuredItems() throws Exception {
        Element component = loadComponent(baseConfigXml());
        PresetDefinition preset = loadPreset("""
                <?xml version="1.0" encoding="UTF-8"?>
                <preset xmlns="http://www.example.org/chibiforge_preset/"
                        name="Pins"
                        id="org.chibios.chibiforge.components.hal.stm32f4xx"
                        version="1.0.0">
                  <sections>
                    <section name="Pin Settings">
                      <property name="pins" type="list">
                        <items>
                          <item>
                            <sections>
                              <section name="Pin Details">
                                <property name="name" type="string">
                                  <value>PA2</value>
                                </property>
                                <property name="mode" type="enum">
                                  <value>OUTPUT</value>
                                </property>
                              </section>
                            </sections>
                          </item>
                          <item>
                            <sections>
                              <section name="Pin Details">
                                <property name="name" type="string">
                                  <value>PA3</value>
                                </property>
                                <property name="mode" type="enum">
                                  <value>ANALOG</value>
                                </property>
                                <property name="speed" type="enum">
                                  <value>VERY_HIGH</value>
                                </property>
                              </section>
                            </sections>
                          </item>
                        </items>
                      </property>
                    </section>
                  </sections>
                </preset>
                """);

        PresetApplyReport report = applier.apply(preset, definition, component);

        assertThat(report.updatedCount()).isEqualTo(1);
        Element pins = directChild(directChild(component, "pin_settings"), "pins");
        assertThat(elementChildren(pins)).hasSize(2);

        Element firstItem = elementChildren(pins).get(0);
        Element firstSection = directChild(firstItem, "pin_details");
        assertThat(textOf(firstSection, "name")).isEqualTo("PA2");
        assertThat(textOf(firstSection, "mode")).isEqualTo("OUTPUT");
        assertThat(textOf(firstSection, "speed")).isEqualTo("LOW");

        Element secondItem = elementChildren(pins).get(1);
        Element secondSection = directChild(secondItem, "pin_details");
        assertThat(textOf(secondSection, "name")).isEqualTo("PA3");
        assertThat(textOf(secondSection, "mode")).isEqualTo("ANALOG");
        assertThat(textOf(secondSection, "speed")).isEqualTo("VERY_HIGH");
    }

    @Test
    void ignoresUnknownNestedListPropertiesWithWarning() throws Exception {
        Element component = loadComponent(baseConfigXml());
        PresetDefinition preset = loadPreset("""
                <?xml version="1.0" encoding="UTF-8"?>
                <preset xmlns="http://www.example.org/chibiforge_preset/"
                        name="Pins"
                        id="org.chibios.chibiforge.components.hal.stm32f4xx"
                        version="1.0.0">
                  <sections>
                    <section name="Pin Settings">
                      <property name="pins" type="list">
                        <items>
                          <item>
                            <sections>
                              <section name="Pin Details">
                                <property name="name" type="string">
                                  <value>PA8</value>
                                </property>
                                <property name="legacy_mode" type="enum">
                                  <value>OUTPUT</value>
                                </property>
                              </section>
                            </sections>
                          </item>
                        </items>
                      </property>
                    </section>
                  </sections>
                </preset>
                """);

        PresetApplyReport report = applier.apply(preset, definition, component);

        assertThat(report.updatedCount()).isEqualTo(1);
        assertThat(report.warnings()).singleElement().satisfies(warning -> {
            assertThat(warning).contains("Pin Settings / pins / item[1] / Pin Details / legacy_mode");
            assertThat(warning).contains("path not present");
        });
        Element pins = directChild(directChild(component, "pin_settings"), "pins");
        Element item = elementChildren(pins).get(0);
        Element section = directChild(item, "pin_details");
        assertThat(textOf(section, "name")).isEqualTo("PA8");
        assertThat(textOf(section, "mode")).isEqualTo("INPUT");
        assertThat(textOf(section, "speed")).isEqualTo("LOW");
    }

    private Element loadComponent(String xml) throws Exception {
        ConfigLoader.LoadedConfiguration loaded = configLoader.loadWithDocument(
                new ByteArrayInputStream(xml.stripLeading().getBytes(StandardCharsets.UTF_8)));
        ComponentConfigEntry entry = loaded.configuration().getComponents().get(0);
        return entry.getConfigElement();
    }

    private PresetDefinition loadPreset(String xml) throws Exception {
        return presetLoader.load(new ByteArrayInputStream(xml.stripLeading().getBytes(StandardCharsets.UTF_8)));
    }

    private String baseConfigXml() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <chibiforgeConfiguration xmlns="http://chibiforge/schema/config" toolVersion="1.0.0" schemaVersion="1.0">
                  <components>
                    <component id="org.chibios.chibiforge.components.hal.stm32f4xx">
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

    private java.util.List<Element> elementChildren(Element parent) {
        java.util.List<Element> children = new java.util.ArrayList<>();
        for (int i = 0; i < parent.getChildNodes().getLength(); i++) {
            if (parent.getChildNodes().item(i) instanceof Element child) {
                children.add(child);
            }
        }
        return children;
    }
}
