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
                    <component id="test.hello">
                      <settings>
                        <greeting>Hi there</greeting>
                        <count>5</count>
                        <enabled>true</enabled>
                      </settings>
                    </component>
                  </components>
                </chibiforgeConfiguration>
                """);

        GenerationContext ctx = new GenerationContext(configRoot, "default", false, false);
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

        // 5. Report has expected action counts
        assertThat(report.countByType(GenerationAction.Type.COPY)).isGreaterThanOrEqualTo(3);
        assertThat(report.countByType(GenerationAction.Type.TEMPLATE)).isEqualTo(1);
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
                    <component id="test.hello">
                      <settings>
                        <greeting>Hello</greeting>
                        <count>3</count>
                        <enabled>false</enabled>
                      </settings>
                    </component>
                  </components>
                </chibiforgeConfiguration>
                """);

        GenerationContext ctx = new GenerationContext(configRoot, "default", false, false);
        GeneratorEngine engine = new GeneratorEngine();

        // First run
        engine.generate(ctx, componentsRoot);
        Path onceFile = configRoot.resolve("main.c");
        assertThat(onceFile).exists();

        // Modify the write-once file
        Files.writeString(onceFile, "/* user modified */");

        // Second run
        GenerationReport report2 = engine.generate(ctx, componentsRoot);

        // Write-once file should NOT be overwritten
        assertThat(Files.readString(onceFile)).isEqualTo("/* user modified */");
        assertThat(report2.countByType(GenerationAction.Type.SKIP)).isGreaterThanOrEqualTo(1);
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
                    <component id="test.hello">
                      <settings>
                        <greeting>Hello</greeting>
                        <count>1</count>
                        <enabled>true</enabled>
                      </settings>
                    </component>
                  </components>
                </chibiforgeConfiguration>
                """);

        GenerationContext ctx = new GenerationContext(configRoot, "default", true, false);
        GeneratorEngine engine = new GeneratorEngine();
        GenerationReport report = engine.generate(ctx, componentsRoot);

        // No generated directory should be created
        assertThat(configRoot.resolve("generated")).doesNotExist();
        assertThat(configRoot.resolve("main.c")).doesNotExist();
        assertThat(configRoot.resolve("Makefile.inc")).doesNotExist();

        // But actions are recorded
        assertThat(report.getActions()).isNotEmpty();
    }
}
