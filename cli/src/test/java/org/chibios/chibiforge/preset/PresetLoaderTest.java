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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PresetLoaderTest {

    private final PresetLoader loader = new PresetLoader();

    @Test
    void parsesValidScalarPresetFixture() throws Exception {
        PresetDefinition preset = loadFixture("scalar_patch.xml");

        assertThat(preset.name()).isEqualTo("Scalar Patch");
        assertThat(preset.componentId()).isEqualTo("org.chibios.chibiforge.components.hal.stm32f4xx");
        assertThat(preset.version()).isEqualTo("1.0.0");
        assertThat(preset.sections()).hasSize(1);

        PresetSection section = preset.sections().get(0);
        assertThat(section.name()).isEqualTo("Initialization Settings");
        assertThat(section.properties()).hasSize(2);

        PresetProperty scalar = section.properties().get(0);
        assertThat(scalar.name()).isEqualTo("hse_frequency");
        assertThat(scalar.type()).isEqualTo(PropertyDef.Type.INT);
        assertThat(scalar.value()).isEqualTo("12000000");
        assertThat(scalar.items()).isEmpty();

        PresetProperty text = section.properties().get(1);
        assertThat(text.type()).isEqualTo(PropertyDef.Type.TEXT);
        assertThat(text.value()).isEqualTo("Updated notes");
    }

    @Test
    void parsesValidStructuredListPresetFixture() throws Exception {
        PresetDefinition preset = loadFixture("structured_list_replace.xml");

        assertThat(preset.name()).isEqualTo("Structured Pin List");
        assertThat(preset.componentId()).isEqualTo("org.chibios.chibiforge.components.hal.stm32f4xx");
        assertThat(preset.sections()).singleElement().satisfies(section -> {
            assertThat(section.name()).isEqualTo("Pin Settings");
            assertThat(section.properties()).singleElement().satisfies(list -> {
                assertThat(list.name()).isEqualTo("pins");
                assertThat(list.type()).isEqualTo(PropertyDef.Type.LIST);
                assertThat(list.items()).hasSize(2);
            });
        });

        PresetProperty list = preset.sections().get(0).properties().get(0);
        assertThat(list.type()).isEqualTo(PropertyDef.Type.LIST);
        assertThat(list.value()).isNull();
        assertThat(list.items()).hasSize(2);
        assertThat(list.items().get(0).sections()).singleElement().satisfies(itemSection -> {
            assertThat(itemSection.name()).isEqualTo("Pin Details");
            assertThat(itemSection.properties()).extracting(PresetProperty::name)
                    .containsExactly("name", "mode");
        });
        assertThat(list.items().get(1).sections()).singleElement().satisfies(itemSection -> {
            assertThat(itemSection.name()).isEqualTo("Pin Details");
            assertThat(itemSection.properties()).extracting(PresetProperty::name)
                    .containsExactly("name", "mode", "speed");
        });
    }

    @Test
    void rejectsPresetThatDoesNotConformToSchema() {
        assertThatThrownBy(() -> load("""
                <?xml version="1.0" encoding="UTF-8"?>
                <preset xmlns="http://chibiforge/schema/preset"
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
                <preset xmlns="http://chibiforge/schema/preset"
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
                <preset xmlns="http://chibiforge/schema/preset"
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
        PresetDefinition preset = loadFixture("empty_list.xml");

        PresetProperty list = preset.sections().get(0).properties().get(0);
        assertThat(list.type()).isEqualTo(PropertyDef.Type.LIST);
        assertThat(list.items()).isEmpty();
        assertThat(list.hasItems()).isTrue();
    }

    private PresetDefinition load(String xml) throws Exception {
        return loader.load(new ByteArrayInputStream(xml.stripLeading().getBytes(StandardCharsets.UTF_8)));
    }

    private PresetDefinition loadFixture(String fixtureName) throws Exception {
        try (InputStream is = PresetLoaderTest.class.getResourceAsStream(
                "/fixtures/presets/hal-stm32f4xx/" + fixtureName)) {
            if (is == null) {
                throw new IllegalArgumentException("Missing preset fixture '" + fixtureName + "'");
            }
            return loader.load(is);
        }
    }
}
