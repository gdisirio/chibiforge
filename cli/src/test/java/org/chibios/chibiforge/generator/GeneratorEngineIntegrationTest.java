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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratorEngineIntegrationTest {

    private Path componentsRoot;

    @BeforeEach
    void setup() throws Exception {
        componentsRoot = Paths.get(
                getClass().getResource("/fixtures/integration/components").toURI());
    }

    @Test
    void fullPipelineProducesExpectedOutput(@TempDir Path configRoot) throws Exception {
        // Write the config file
        Files.writeString(configRoot.resolve("chibiforge.xcfg"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <chibiforgeConfiguration
                    xmlns="http://chibiforge/schema/config"
                    toolVersion="1.0.0"
                    schemaVersion="1.0">
                  <targets>
                    <target id="default"/>
                  </targets>
                  <components>
                    <component id="test.hello" version="1.0.0">
                      <settings>
                        <greeting>Hi there</greeting>
                        <count>5</count>
                        <enabled>true</enabled>
                      </settings>
                    </component>
                  </components>
                </chibiforgeConfiguration>
                """);

        GenerationContext ctx = new GenerationContext(configRoot.resolve("chibiforge.xcfg"), configRoot, "default", false, false);
        GeneratorEngine engine = new GeneratorEngine();
        GenerationReport report = engine.generate(ctx, componentsRoot);

        // 1. Static source file copied to generated/<normalizedId>/
        Path staticFile = configRoot.resolve("generated/test_hello/hello.c");
        assertThat(staticFile).exists();
        assertThat(Files.readString(staticFile)).contains("void hello(void)");

        // 2. source_root_wa file copied to config root (always)
        Path alwaysFile = configRoot.resolve("Makefile.inc");
        assertThat(alwaysFile).exists();
        assertThat(Files.readString(alwaysFile)).contains("HELLO_SRC");

        // 3. source_root_wo file copied to config root (write-once)
        Path onceFile = configRoot.resolve("main.c");
        assertThat(onceFile).exists();
        assertThat(Files.readString(onceFile)).contains("hello()");

        // 4. Template processed with resolved values
        Path generatedHeader = configRoot.resolve("generated/config.h");
        assertThat(generatedHeader).exists();
        String headerContent = Files.readString(generatedHeader);
        assertThat(headerContent).contains("#define GREETING \"Hi there\"");
        assertThat(headerContent).contains("#define COUNT 5");
        assertThat(headerContent).contains("#define ENABLED true");
        assertThat(headerContent).contains("#define TARGET \"default\"");

        // 5. Code-first (.ftlc) template processed
        Path definesHeader = configRoot.resolve("generated/defines.h");
        assertThat(definesHeader).exists();
        String definesContent = Files.readString(definesHeader);
        assertThat(definesContent).contains("#ifndef DEFINES_H");
        assertThat(definesContent).contains("/* Greeting: Hi there */");
        assertThat(definesContent).contains("#define LARGE_COUNT 0"); // count=5 <= 10
        assertThat(definesContent).contains("#define COUNT_VALUE 5");

        // 6. cfg_root_wa/ template processed to config root (always)
        Path rootWaTemplate = configRoot.resolve("generated_makefile.mk");
        assertThat(rootWaTemplate).exists();
        String makeContent = Files.readString(rootWaTemplate);
        assertThat(makeContent).contains("GREETING = Hi there");
        assertThat(makeContent).contains("COUNT = 5");

        // 7. cfg_root_wo/ template processed to config root (write-once)
        Path rootWoTemplate = configRoot.resolve("user_config.h");
        assertThat(rootWoTemplate).exists();
        assertThat(Files.readString(rootWoTemplate)).contains("#define DEFAULT_GREETING \"Hi there\"");

        // 8. Report has expected action counts
        assertThat(report.countByType(GenerationAction.Type.COPY)).isGreaterThanOrEqualTo(3);
        assertThat(report.countByType(GenerationAction.Type.TEMPLATE)).isEqualTo(4); // 2 cfg + 1 cfg_root_wa + 1 cfg_root_wo
    }

    @Test
    void writeOncePreservedOnRerun(@TempDir Path configRoot) throws Exception {
        Files.writeString(configRoot.resolve("chibiforge.xcfg"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <chibiforgeConfiguration
                    xmlns="http://chibiforge/schema/config"
                    toolVersion="1.0.0"
                    schemaVersion="1.0">
                  <targets><target id="default"/></targets>
                  <components>
                    <component id="test.hello" version="1.0.0">
                      <settings>
                        <greeting>Hello</greeting>
                        <count>3</count>
                        <enabled>false</enabled>
                      </settings>
                    </component>
                  </components>
                </chibiforgeConfiguration>
                """);

        GenerationContext ctx = new GenerationContext(configRoot.resolve("chibiforge.xcfg"), configRoot, "default", false, false);
        GeneratorEngine engine = new GeneratorEngine();

        // First run
        engine.generate(ctx, componentsRoot);
        Path onceFile = configRoot.resolve("main.c");
        assertThat(onceFile).exists();

        // Modify the write-once file
        Files.writeString(onceFile, "/* user modified */");

        // Second run
        GenerationReport report2 = engine.generate(ctx, componentsRoot);

        // Write-once static file should NOT be overwritten
        assertThat(Files.readString(onceFile)).isEqualTo("/* user modified */");

        // Write-once template output should also be preserved
        Path woTemplate = configRoot.resolve("user_config.h");
        assertThat(woTemplate).exists();
        Files.writeString(woTemplate, "/* user edited template output */");

        // Third run
        GenerationReport report3 = engine.generate(ctx, componentsRoot);
        assertThat(Files.readString(woTemplate)).isEqualTo("/* user edited template output */");

        // Skips should include both static and template write-once files
        assertThat(report2.countByType(GenerationAction.Type.SKIP)).isGreaterThanOrEqualTo(1);
        assertThat(report3.countByType(GenerationAction.Type.SKIP)).isGreaterThanOrEqualTo(2);
    }

    @Test
    void dryRunProducesNoFiles(@TempDir Path configRoot) throws Exception {
        Files.writeString(configRoot.resolve("chibiforge.xcfg"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <chibiforgeConfiguration
                    xmlns="http://chibiforge/schema/config"
                    toolVersion="1.0.0"
                    schemaVersion="1.0">
                  <targets><target id="default"/></targets>
                  <components>
                    <component id="test.hello" version="1.0.0">
                      <settings>
                        <greeting>Hello</greeting>
                        <count>1</count>
                        <enabled>true</enabled>
                      </settings>
                    </component>
                  </components>
                </chibiforgeConfiguration>
                """);

        GenerationContext ctx = new GenerationContext(configRoot.resolve("chibiforge.xcfg"), configRoot, "default", true, false);
        GeneratorEngine engine = new GeneratorEngine();
        GenerationReport report = engine.generate(ctx, componentsRoot);

        // No generated directory should be created
        assertThat(configRoot.resolve("generated")).doesNotExist();
        assertThat(configRoot.resolve("main.c")).doesNotExist();
        assertThat(configRoot.resolve("Makefile.inc")).doesNotExist();

        // But actions are recorded
        assertThat(report.getActions()).isNotEmpty();
    }

    private static final String MULTITARGET_XCFG = """
            <?xml version="1.0" encoding="UTF-8"?>
            <chibiforgeConfiguration
                xmlns="http://chibiforge/schema/config"
                toolVersion="1.0.0"
                schemaVersion="1.0">
              <targets>
                <target id="default"/>
                <target id="debug"/>
                <target id="release"/>
              </targets>
              <components>
                <component id="test.hello" version="1.0.0">
                  <settings>
                    <greeting>Hello</greeting>
                    <count default="5">
                      <targetValue target="debug">42</targetValue>
                      <targetValue target="release">1</targetValue>
                    </count>
                    <enabled default="true">
                      <targetValue target="debug">false</targetValue>
                    </enabled>
                  </settings>
                </component>
              </components>
            </chibiforgeConfiguration>
            """;

    @Test
    void multiTarget_defaultProducesDefaultValues(@TempDir Path configRoot) throws Exception {
        Files.writeString(configRoot.resolve("chibiforge.xcfg"), MULTITARGET_XCFG);

        GenerationContext ctx = new GenerationContext(configRoot.resolve("chibiforge.xcfg"), configRoot, "default", false, false);
        new GeneratorEngine().generate(ctx, componentsRoot);

        String content = Files.readString(configRoot.resolve("generated/config.h"));
        assertThat(content).contains("#define COUNT 5");
        assertThat(content).contains("#define ENABLED true");
    }

    @Test
    void multiTarget_debugProducesDebugValues(@TempDir Path configRoot) throws Exception {
        Files.writeString(configRoot.resolve("chibiforge.xcfg"), MULTITARGET_XCFG);

        GenerationContext ctx = new GenerationContext(configRoot.resolve("chibiforge.xcfg"), configRoot, "debug", false, false);
        new GeneratorEngine().generate(ctx, componentsRoot);

        String content = Files.readString(configRoot.resolve("generated/config.h"));
        assertThat(content).contains("#define COUNT 42");
        assertThat(content).contains("#define ENABLED false");
        assertThat(content).contains("#define TARGET \"debug\"");
    }

    @Test
    void multiTarget_releaseUsesOverrideAndFallback(@TempDir Path configRoot) throws Exception {
        Files.writeString(configRoot.resolve("chibiforge.xcfg"), MULTITARGET_XCFG);

        GenerationContext ctx = new GenerationContext(configRoot.resolve("chibiforge.xcfg"), configRoot, "release", false, false);
        new GeneratorEngine().generate(ctx, componentsRoot);

        String content = Files.readString(configRoot.resolve("generated/config.h"));
        assertThat(content).contains("#define COUNT 1");      // release override
        assertThat(content).contains("#define ENABLED true");  // no release override, falls back to default
    }
}
