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

package org.chibios.chibiforge.component;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComponentDefinitionParserTest {

    private static ComponentDefinition def;

    @BeforeAll
    static void parseFixture() throws Exception {
        ComponentDefinitionParser parser = new ComponentDefinitionParser();
        try (InputStream is = ComponentDefinitionParserTest.class.getResourceAsStream(
                "/fixtures/simple-component/component/schema.xml")) {
            def = parser.parse(is);
        }
    }

    @Test
    void rootAttributes() {
        assertThat(def.getId()).isEqualTo("org.chibios.hal.stm32f4xx");
        assertThat(def.getName()).isEqualTo("HAL STM32F4xx");
        assertThat(def.getVersion()).isEqualTo("1.0.0");
        assertThat(def.isHidden()).isTrue();
        assertThat(def.isPlatform()).isFalse();
    }

    @Test
    void description() {
        assertThat(def.getDescription()).isEqualTo("HAL configuration for STM32F4xx devices.");
    }

    @Test
    void resources() {
        assertThat(def.getResources()).hasSize(2);
        assertThat(def.getResources().get(0).getId()).isEqualTo("stm32f4_limits");
        assertThat(def.getResources().get(0).getFile()).isEqualTo("resources/stm32f4_limits.xml");
        assertThat(def.getResources().get(1).getId()).isEqualTo("phy_catalog");
        assertThat(def.getResources().get(1).getFile()).isEqualTo("resources/phy_catalog.json");
    }

    @Test
    void categories() {
        assertThat(def.getCategories()).containsExactly("HAL/Platforms/STM32F4xx", "RTOS");
    }

    @Test
    void requires() {
        assertThat(def.getRequires()).hasSize(1);
        assertThat(def.getRequires().get(0).getId()).isEqualTo("features.hal.core");
        assertThat(def.getRequires().get(0).isExclusive()).isFalse();
    }

    @Test
    void provides() {
        assertThat(def.getProvides()).hasSize(1);
        assertThat(def.getProvides().get(0).getId()).isEqualTo("features.hal.platform.stm32f4xx");
        assertThat(def.getProvides().get(0).isExclusive()).isTrue();
    }

    @Test
    void componentLevelImage() {
        assertThat(def.getImages()).hasSize(1);
        ImageDef img = def.getImages().get(0);
        assertThat(img.getFile()).isEqualTo("rsc/block_diagram.png");
        assertThat(img.getAlign()).isEqualTo("center");
        assertThat(img.getText()).isEqualTo("Block diagram of the peripheral");
    }

    @Test
    void sectionsCount() {
        assertThat(def.getSections()).hasSize(2);
    }

    @Test
    void initializationSection() {
        SectionDef section = def.getSections().get(0);
        assertThat(section.getName()).isEqualTo("Initialization Settings");
        assertThat(section.isExpanded()).isTrue();
        assertThat(section.getDescription()).isEqualTo("Core initialization parameters.");
    }

    @Test
    void boolProperty() {
        SectionDef section = def.getSections().get(0);
        PropertyDef prop = (PropertyDef) section.getChildren().get(0);
        assertThat(prop.getName()).isEqualTo("do_not_init");
        assertThat(prop.getType()).isEqualTo(PropertyDef.Type.BOOL);
        assertThat(prop.isRequired()).isTrue();
        assertThat(prop.isEditable()).isTrue();
        assertThat(prop.getDefaultValue()).isEqualTo("false");
    }

    @Test
    void intPropertyWithConstraints() {
        SectionDef section = def.getSections().get(0);
        PropertyDef prop = (PropertyDef) section.getChildren().get(1);
        assertThat(prop.getName()).isEqualTo("vdd");
        assertThat(prop.getType()).isEqualTo(PropertyDef.Type.INT);
        assertThat(prop.getIntMin()).isEqualTo("180");
        assertThat(prop.getIntMax()).isEqualTo("360");
        assertThat(prop.getDefaultValue()).isEqualTo("300");
    }

    @Test
    void layoutWithChildrenAndEmpty() {
        SectionDef section = def.getSections().get(0);
        LayoutDef layout = (LayoutDef) section.getChildren().get(2);
        assertThat(layout.getColumns()).isEqualTo(2);
        assertThat(layout.getAlign()).isEqualTo("left");
        assertThat(layout.getChildren()).hasSize(4);

        assertThat(layout.getChildren().get(0)).isInstanceOf(PropertyDef.class);
        assertThat(((PropertyDef) layout.getChildren().get(0)).getName()).isEqualTo("hse_frequency");
        assertThat(layout.getChildren().get(1)).isInstanceOf(PropertyDef.class);
        assertThat(layout.getChildren().get(2)).isInstanceOf(ImageDef.class);
        assertThat(layout.getChildren().get(3)).isEqualTo(LayoutDef.EmptySlot.INSTANCE);
    }

    @Test
    void enumProperty() {
        SectionDef section = def.getSections().get(0);
        PropertyDef prop = (PropertyDef) section.getChildren().get(3);
        assertThat(prop.getName()).isEqualTo("system_clock_source");
        assertThat(prop.getType()).isEqualTo(PropertyDef.Type.ENUM);
        assertThat(prop.getEnumOf()).isEqualTo("PLL,HSI,HSE");
    }

    @Test
    void stringPropertyWithRegex() {
        SectionDef section = def.getSections().get(0);
        PropertyDef prop = (PropertyDef) section.getChildren().get(4);
        assertThat(prop.getName()).isEqualTo("board_name");
        assertThat(prop.getType()).isEqualTo(PropertyDef.Type.STRING);
        assertThat(prop.getStringRegex()).isEqualTo("[A-Za-z0-9_]+");
    }

    @Test
    void textPropertyWithMaxsize() {
        SectionDef section = def.getSections().get(0);
        PropertyDef prop = (PropertyDef) section.getChildren().get(5);
        assertThat(prop.getName()).isEqualTo("notes");
        assertThat(prop.getType()).isEqualTo(PropertyDef.Type.TEXT);
        assertThat(prop.getTextMaxsize()).isEqualTo("4096");
        assertThat(prop.isRequired()).isFalse();
    }

    @Test
    void listPropertyWithNestedSections() {
        SectionDef pinSection = def.getSections().get(1);
        assertThat(pinSection.getName()).isEqualTo("Pin Settings");
        assertThat(pinSection.isExpanded()).isFalse();

        PropertyDef pins = (PropertyDef) pinSection.getChildren().get(0);
        assertThat(pins.getName()).isEqualTo("pins");
        assertThat(pins.getType()).isEqualTo(PropertyDef.Type.LIST);
        assertThat(pins.getListColumns()).isEqualTo("name:150,mode:100,speed");

        List<SectionDef> nested = pins.getNestedSections();
        assertThat(nested).hasSize(1);
        assertThat(nested.get(0).getName()).isEqualTo("Pin Details");

        List<Object> pinChildren = nested.get(0).getChildren();
        assertThat(pinChildren).hasSize(3);
        assertThat(((PropertyDef) pinChildren.get(0)).getName()).isEqualTo("name");
        assertThat(((PropertyDef) pinChildren.get(1)).getName()).isEqualTo("mode");
        assertThat(((PropertyDef) pinChildren.get(2)).getName()).isEqualTo("speed");
    }
}
