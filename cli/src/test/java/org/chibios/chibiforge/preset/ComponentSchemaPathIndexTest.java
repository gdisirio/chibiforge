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
import org.chibios.chibiforge.component.PropertyDef;
import org.chibios.chibiforge.component.SectionDef;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComponentSchemaPathIndexTest {

    private static ComponentDefinition fixtureDefinition;

    @BeforeAll
    static void loadFixture() throws Exception {
        ComponentDefinitionParser parser = new ComponentDefinitionParser();
        try (InputStream is = ComponentSchemaPathIndexTest.class.getResourceAsStream(
                "/fixtures/org.chibios.chibiforge.components.hal.stm32f4xx/component/schema.xml")) {
            fixtureDefinition = parser.parse(is);
        }
    }

    @Test
    void indexesNormalizedPathsAcrossSectionsAndLayouts() {
        ComponentSchemaPathIndex index = ComponentSchemaPathIndex.from(fixtureDefinition);

        assertThat(index.getPresetMatchablePaths())
                .extracting(ComponentSchemaPathIndex.SchemaPropertyPath::normalizedPath)
                .contains(
                        "initialization_settings/do_not_init",
                        "initialization_settings/vdd",
                        "initialization_settings/hse_frequency",
                        "initialization_settings/lse_frequency",
                        "initialization_settings/system_clock_source",
                        "initialization_settings/board_name",
                        "initialization_settings/notes",
                        "pin_settings/pins");
    }

    @Test
    void keepsDuplicatePropertyNamesDistinctAcrossSections() {
        ComponentDefinition definition = new ComponentDefinition(
                "test.component",
                "Test",
                "1.0.0",
                false,
                false,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        section("Clock Settings", property("Channels")),
                        section("DMA Settings", property("Channels"))));

        ComponentSchemaPathIndex index = ComponentSchemaPathIndex.from(definition);

        assertThat(index.getPathsByNormalizedPath().keySet())
                .containsExactly("clock_settings/channels", "dma_settings/channels");
    }

    @Test
    void indexesNestedListSectionPathsUnderOwningListProperty() {
        ComponentSchemaPathIndex index = ComponentSchemaPathIndex.from(fixtureDefinition);

        assertThat(index.find("pin_settings/pins")).isPresent();
        assertThat(index.find("pin_settings/pins/pin_details/name")).isPresent();
        assertThat(index.find("pin_settings/pins/pin_details/mode")).isPresent();
        assertThat(index.find("pin_settings/pins/pin_details/speed")).isPresent();

        ComponentSchemaPathIndex.SchemaPropertyPath nested = index.find("pin_settings/pins/pin_details/name").orElseThrow();
        assertThat(nested.isNestedUnderList()).isTrue();
        assertThat(nested.ownerListPath()).isEqualTo("pin_settings/pins");
        assertThat(nested.displayPath()).isEqualTo("Pin Settings / pins / Pin Details / name");
    }

    @Test
    void rejectsCollidingNormalizedPaths() {
        ComponentDefinition definition = new ComponentDefinition(
                "test.component",
                "Test",
                "1.0.0",
                false,
                false,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(
                        section("DMA Settings", property("PLL old mode")),
                        section("DMA_Settings", property("PLL old mode"))));

        assertThatThrownBy(() -> ComponentSchemaPathIndex.from(definition))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Normalized schema path collision");
    }

    private static SectionDef section(String name, PropertyDef... properties) {
        return new SectionDef(name, true, "true", "true", null, List.of(properties));
    }

    private static PropertyDef property(String name) {
        return new PropertyDef(name, PropertyDef.Type.STRING, "brief", true,
                "true", "true", "", null, null, null,
                null, null, null, List.of());
    }
}
