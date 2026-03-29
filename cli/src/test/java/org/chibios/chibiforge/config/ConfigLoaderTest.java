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

package org.chibios.chibiforge.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigLoaderTest {

    private final ConfigLoader loader = new ConfigLoader();

    @Test
    void simpleConfig() throws Exception {
        ChibiForgeConfiguration config = loadFixture("simple.xcfg");
        assertThat(config.getToolVersion()).isEqualTo("1.0.0");
        assertThat(config.getSchemaVersion()).isEqualTo("1.0");
        assertThat(config.getTargets()).containsExactly("default");
        assertThat(config.getComponents()).hasSize(1);
        assertThat(config.getComponents().get(0).getComponentId())
                .isEqualTo("org.chibios.hal.stm32f4xx");
    }

    @Test
    void multiComponentConfig() throws Exception {
        ChibiForgeConfiguration config = loadFixture("multi.xcfg");
        assertThat(config.getTargets()).containsExactly("default", "debug", "release");
        assertThat(config.getComponents()).hasSize(2);
        assertThat(config.getComponents().get(0).getComponentId())
                .isEqualTo("org.chibios.hal.stm32f4xx");
        assertThat(config.getComponents().get(1).getComponentId())
                .isEqualTo("org.chibios.board.stm32f4xx");
    }

    @Test
    void configElementPreservesChildren() throws Exception {
        ChibiForgeConfiguration config = loadFixture("simple.xcfg");
        var element = config.getComponents().get(0).getConfigElement();
        assertThat(element.getElementsByTagName("vdd").getLength()).isEqualTo(1);
        assertThat(element.getElementsByTagName("vdd").item(0).getTextContent()).isEqualTo("300");
    }

    @Test
    void targetResolver_validTarget() throws Exception {
        ChibiForgeConfiguration config = loadFixture("multi.xcfg");
        TargetResolver resolver = new TargetResolver();
        assertThat(resolver.resolve(config, "debug")).isEqualTo("debug");
        assertThat(resolver.resolve(config, "default")).isEqualTo("default");
    }

    @Test
    void targetResolver_nullDefaultsToDefault() throws Exception {
        ChibiForgeConfiguration config = loadFixture("simple.xcfg");
        TargetResolver resolver = new TargetResolver();
        assertThat(resolver.resolve(config, null)).isEqualTo("default");
    }

    @Test
    void targetResolver_invalidTarget() throws Exception {
        ChibiForgeConfiguration config = loadFixture("simple.xcfg");
        TargetResolver resolver = new TargetResolver();
        assertThatThrownBy(() -> resolver.resolve(config, "nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonexistent");
    }

    private ChibiForgeConfiguration loadFixture(String name) throws Exception {
        try (InputStream is = getClass().getResourceAsStream("/fixtures/configs/" + name)) {
            return loader.load(is);
        }
    }
}
