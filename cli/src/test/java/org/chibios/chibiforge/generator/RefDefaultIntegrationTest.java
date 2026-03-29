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

package org.chibios.chibiforge.generator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class RefDefaultIntegrationTest {

    private static Path componentsRoot;

    @BeforeAll
    static void setup() throws Exception {
        componentsRoot = Paths.get(
                RefDefaultIntegrationTest.class.getResource("/fixtures/integration/components").toURI());
    }

    @Test
    void refDefaultsAppliedWhenPropertiesMissing(@TempDir Path configRoot) throws Exception {
        // Config omits max_channels and voltage — they should get @ref: resolved defaults
        Files.writeString(configRoot.resolve("chibiforge.xcfg"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <chibiforgeConfiguration
                    xmlns="http://chibiforge/schema/config"
                    toolVersion="1.0.0"
                    schemaVersion="1.0">
                  <targets><target id="default"/></targets>
                  <components>
                    <component id="test.refdefault">
                      <settings>
                        <label>custom_label</label>
                      </settings>
                    </component>
                  </components>
                </chibiforgeConfiguration>
                """);

        GenerationContext ctx = new GenerationContext(configRoot.resolve("chibiforge.xcfg"), configRoot, "default", false, false);
        new GeneratorEngine().generate(ctx, componentsRoot);

        String content = Files.readString(configRoot.resolve("generated/refout.h"));
        // @ref: defaults resolved from limits.xml
        assertThat(content).contains("#define MAX_CHANNELS 16");
        assertThat(content).contains("#define VOLTAGE 300");
        // Explicit value from xcfg preserved
        assertThat(content).contains("#define LABEL \"custom_label\"");
    }

    @Test
    void explicitValuesOverrideRefDefaults(@TempDir Path configRoot) throws Exception {
        // Config provides explicit values — @ref: defaults should NOT be used
        Files.writeString(configRoot.resolve("chibiforge.xcfg"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <chibiforgeConfiguration
                    xmlns="http://chibiforge/schema/config"
                    toolVersion="1.0.0"
                    schemaVersion="1.0">
                  <targets><target id="default"/></targets>
                  <components>
                    <component id="test.refdefault">
                      <settings>
                        <max_channels>8</max_channels>
                        <voltage>250</voltage>
                        <label>explicit</label>
                      </settings>
                    </component>
                  </components>
                </chibiforgeConfiguration>
                """);

        GenerationContext ctx = new GenerationContext(configRoot.resolve("chibiforge.xcfg"), configRoot, "default", false, false);
        new GeneratorEngine().generate(ctx, componentsRoot);

        String content = Files.readString(configRoot.resolve("generated/refout.h"));
        // Explicit values used, not @ref: defaults
        assertThat(content).contains("#define MAX_CHANNELS 8");
        assertThat(content).contains("#define VOLTAGE 250");
        assertThat(content).contains("#define LABEL \"explicit\"");
    }

    @Test
    void plainDefaultAppliedWhenPropertyMissing(@TempDir Path configRoot) throws Exception {
        // Config omits all properties — both @ref: and plain defaults should apply
        Files.writeString(configRoot.resolve("chibiforge.xcfg"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <chibiforgeConfiguration
                    xmlns="http://chibiforge/schema/config"
                    toolVersion="1.0.0"
                    schemaVersion="1.0">
                  <targets><target id="default"/></targets>
                  <components>
                    <component id="test.refdefault">
                      <settings/>
                    </component>
                  </components>
                </chibiforgeConfiguration>
                """);

        GenerationContext ctx = new GenerationContext(configRoot.resolve("chibiforge.xcfg"), configRoot, "default", false, false);
        new GeneratorEngine().generate(ctx, componentsRoot);

        String content = Files.readString(configRoot.resolve("generated/refout.h"));
        assertThat(content).contains("#define MAX_CHANNELS 16");
        assertThat(content).contains("#define VOLTAGE 300");
        assertThat(content).contains("#define LABEL \"fixed_value\"");
    }
}
