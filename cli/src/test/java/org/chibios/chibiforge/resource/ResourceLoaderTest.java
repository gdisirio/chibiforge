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

package org.chibios.chibiforge.resource;

import com.fasterxml.jackson.databind.JsonNode;
import org.chibios.chibiforge.component.ComponentDefinition;
import org.chibios.chibiforge.component.ComponentDefinitionParser;
import org.chibios.chibiforge.container.FilesystemContent;
import org.w3c.dom.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResourceLoaderTest {

    private static ComponentDefinition def;
    private static FilesystemContent content;

    @BeforeAll
    static void setup() throws Exception {
        Path componentDir = Paths.get(
                ResourceLoaderTest.class.getResource("/fixtures/simple-component/component").toURI());
        content = new FilesystemContent(componentDir);

        ComponentDefinitionParser parser = new ComponentDefinitionParser();
        try (InputStream is = content.open("schema.xml")) {
            def = parser.parse(is);
        }
    }

    @Test
    void loadsXmlResource() throws Exception {
        ResourceLoader loader = new ResourceLoader();
        Map<String, Object> resources = loader.loadResources(def, content);

        assertThat(resources).containsKey("stm32f4_limits");
        assertThat(resources.get("stm32f4_limits")).isInstanceOf(Document.class);
    }

    @Test
    void loadsJsonResource() throws Exception {
        ResourceLoader loader = new ResourceLoader();
        Map<String, Object> resources = loader.loadResources(def, content);

        assertThat(resources).containsKey("phy_catalog");
        assertThat(resources.get("phy_catalog")).isInstanceOf(JsonNode.class);
    }

    @Test
    void xmlResourceContent() throws Exception {
        ResourceLoader loader = new ResourceLoader();
        Map<String, Object> resources = loader.loadResources(def, content);

        String value = RefExpression.resolve("@ref:stm32f4_limits/stm32f4_limits/adc/@max_channels", resources);
        assertThat(value).isEqualTo("16");
    }

    @Test
    void xmlResourceMultipleAttributes() throws Exception {
        ResourceLoader loader = new ResourceLoader();
        Map<String, Object> resources = loader.loadResources(def, content);

        String value = RefExpression.resolve("@ref:stm32f4_limits/stm32f4_limits/dma/@max_streams", resources);
        assertThat(value).isEqualTo("8");
    }
}
