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

import org.chibios.chibiforge.component.PropertyDef;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PresetLoaderTest {

    private final PresetLoader loader = new PresetLoader();

    @Test
    void parsesValidPresetWithScalarAndListProperties() throws Exception {
        PresetDefinition preset = load("""
                <?xml version="1.0" encoding="UTF-8"?>
                <preset xmlns="http://www.example.org/chibiforge_preset/"
                        name="Discovery Board"
                        id="org.chibios.chibiforge.components.board.stm32f4xx"
                        version="1.0.0">
                  <sections>
                    <section name="Board Settings">
                      <property name="Board name" type="string">
                        <value>STM32F4 Discovery</value>
                      </property>
                      <property name="Boot notes" type="text">
                        <value><![CDATA[Line 1
Line 2]]></value>
                      </property>
                      <property name="Pins" type="list">
                        <items>
                          <item>
                            <sections>
                              <section name="Pin Details">
                                <property name="name" type="string">
                                  <value>PA0</value>
                                </property>
                                <property name="mode" type="enum">
                                  <value>INPUT</value>
                                </property>
                                <property name="speed" type="enum">
                                  <value>LOW</value>
                                </property>
                              </section>
                            </sections>
                          </item>
                          <item>
                            <sections>
                              <section name="Pin Details">
                                <property name="name" type="string">
                                  <value>PA1</value>
                                </property>
                                <property name="mode" type="enum">
                                  <value>OUTPUT</value>
                                </property>
                                <property name="speed" type="enum">
                                  <value>HIGH</value>
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

        assertThat(preset.name()).isEqualTo("Discovery Board");
        assertThat(preset.componentId()).isEqualTo("org.chibios.chibiforge.components.board.stm32f4xx");
        assertThat(preset.version()).isEqualTo("1.0.0");
        assertThat(preset.sections()).hasSize(1);

        PresetSection section = preset.sections().get(0);
        assertThat(section.name()).isEqualTo("Board Settings");
        assertThat(section.properties()).hasSize(3);

        PresetProperty scalar = section.properties().get(0);
        assertThat(scalar.name()).isEqualTo("Board name");
        assertThat(scalar.type()).isEqualTo(PropertyDef.Type.STRING);
        assertThat(scalar.value()).isEqualTo("STM32F4 Discovery");
        assertThat(scalar.items()).isEmpty();

        PresetProperty text = section.properties().get(1);
        assertThat(text.type()).isEqualTo(PropertyDef.Type.TEXT);
        assertThat(text.value()).contains("Line 1").contains("Line 2");

        PresetProperty list = section.properties().get(2);
        assertThat(list.type()).isEqualTo(PropertyDef.Type.LIST);
        assertThat(list.value()).isNull();
        assertThat(list.items()).hasSize(2);
        assertThat(list.items().get(0).sections()).singleElement().satisfies(itemSection -> {
            assertThat(itemSection.name()).isEqualTo("Pin Details");
            assertThat(itemSection.properties()).extracting(PresetProperty::name)
                    .containsExactly("name", "mode", "speed");
        });
    }

    @Test
    void rejectsPresetThatDoesNotConformToSchema() {
        assertThatThrownBy(() -> load("""
                <?xml version="1.0" encoding="UTF-8"?>
                <preset xmlns="http://www.example.org/chibiforge_preset/"
                        name="Invalid"
                        version="1.0.0">
                  <sections>
                    <section name="Board Settings">
                      <property name="Board name" type="string">
                        <value>STM32F4 Discovery</value>
                      </property>
                    </section>
                  </sections>
                </preset>
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Preset does not conform to preset schema");
    }

    @Test
    void rejectsMalformedXml() {
        assertThatThrownBy(() -> load("""
                <?xml version="1.0" encoding="UTF-8"?>
                <preset xmlns="http://www.example.org/chibiforge_preset/"
                        name="Broken"
                        id="test.component"
                        version="1.0.0">
                  <sections>
                    <section name="Board Settings">
                      <property name="Board name" type="string">
                        <value>STM32F4 Discovery</value>
                    </section>
                  </sections>
                </preset>
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid preset XML");
    }

    @Test
    void rejectsListPropertyUsingScalarValue() {
        assertThatThrownBy(() -> load("""
                <?xml version="1.0" encoding="UTF-8"?>
                <preset xmlns="http://www.example.org/chibiforge_preset/"
                        name="Bad List"
                        id="test.component"
                        version="1.0.0">
                  <sections>
                    <section name="Board Settings">
                      <property name="Pins" type="list">
                        <value>not allowed</value>
                      </property>
                    </section>
                  </sections>
                </preset>
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Preset list property 'Pins' must contain nested <items>");
    }

    @Test
    void acceptsEmptyStructuredList() throws Exception {
        PresetDefinition preset = load("""
                <?xml version="1.0" encoding="UTF-8"?>
                <preset xmlns="http://www.example.org/chibiforge_preset/"
                        name="Empty Pins"
                        id="test.component"
                        version="1.0.0">
                  <sections>
                    <section name="Board Settings">
                      <property name="Pins" type="list">
                        <items/>
                      </property>
                    </section>
                  </sections>
                </preset>
                """);

        PresetProperty list = preset.sections().get(0).properties().get(0);
        assertThat(list.type()).isEqualTo(PropertyDef.Type.LIST);
        assertThat(list.items()).isEmpty();
        assertThat(list.hasItems()).isTrue();
    }

    private PresetDefinition load(String xml) throws Exception {
        return loader.load(new ByteArrayInputStream(xml.stripLeading().getBytes(StandardCharsets.UTF_8)));
    }
}
