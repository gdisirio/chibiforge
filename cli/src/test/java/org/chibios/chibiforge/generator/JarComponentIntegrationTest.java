package org.chibios.chibiforge.generator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class JarComponentIntegrationTest {

    private static Path pluginsRoot;

    @BeforeAll
    static void buildTestJar(@TempDir Path tempDir) throws Exception {
        pluginsRoot = tempDir.resolve("plugins");
        Files.createDirectories(pluginsRoot);

        Path jarPath = pluginsRoot.resolve("test.jarcomp.jar");
        try (JarOutputStream jar = new JarOutputStream(new FileOutputStream(jarPath.toFile()))) {
            // plugin.xml with ChibiForge extension point
            addEntry(jar, "plugin.xml", """
                    <plugin>
                      <extension point="org.chibios.chibiforge.component"/>
                    </plugin>
                    """);

            // component/schema.xml
            addEntry(jar, "component/schema.xml", """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <component
                        xmlns="http://chibiforge/schema/component"
                        id="test.jarcomp"
                        name="JAR Component"
                        version="1.0.0"
                        hidden="false"
                        is_platform="false">
                      <description>Component from a JAR.</description>
                      <resources/>
                      <categories><category id="Test"/></categories>
                      <requires/>
                      <provides><feature id="features.test.jarcomp"/></provides>
                      <sections>
                        <section name="settings" expanded="true">
                          <description>Settings.</description>
                          <property name="message" type="string" brief="Message"
                                    required="true" editable="true" default="default"/>
                        </section>
                      </sections>
                    </component>
                    """);

            // component/cfg/jaroutput.h.ftl
            addEntry(jar, "component/cfg/jaroutput.h.ftl", """
                    /* Generated from JAR component */
                    #define JAR_MESSAGE "${doc.settings.message}"
                    """);

            // component/source/jar_static.c
            addEntry(jar, "component/source/jar_static.c", """
                    /* Static file from JAR */
                    void jar_init(void) {}
                    """);

            // component/source_root_wa/jar_always.txt
            addEntry(jar, "component/source_root_wa/jar_always.txt",
                    "JAR always-overwrite\n");

            // component/source_root_wo/jar_once.txt
            addEntry(jar, "component/source_root_wo/jar_once.txt",
                    "JAR write-once\n");
        }
    }

    private static void addEntry(JarOutputStream jar, String name, String content) throws IOException {
        jar.putNextEntry(new JarEntry(name));
        jar.write(content.getBytes());
        jar.closeEntry();
    }

    @Test
    void fullPipelineFromJar(@TempDir Path configRoot) throws Exception {
        Files.writeString(configRoot.resolve("chibiforge.xcfg"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <chibiforgeConfiguration
                    xmlns="http://chibiforge/schema/config"
                    toolVersion="1.0.0"
                    schemaVersion="1.0">
                  <targets><target id="default"/></targets>
                  <components>
                    <component id="test.jarcomp">
                      <settings>
                        <message>Hello from JAR</message>
                      </settings>
                    </component>
                  </components>
                </chibiforgeConfiguration>
                """);

        GenerationContext ctx = new GenerationContext(configRoot, "default", false, false);
        GeneratorEngine engine = new GeneratorEngine();
        GenerationReport report = engine.generate(ctx, null, pluginsRoot);

        // Template output
        Path header = configRoot.resolve("generated/jaroutput.h");
        assertThat(header).exists();
        assertThat(Files.readString(header)).contains("#define JAR_MESSAGE \"Hello from JAR\"");

        // Static source
        Path staticFile = configRoot.resolve("generated/test_jarcomp/jar_static.c");
        assertThat(staticFile).exists();
        assertThat(Files.readString(staticFile)).contains("jar_init");

        // source_root_wa
        Path always = configRoot.resolve("jar_always.txt");
        assertThat(always).exists();

        // source_root_wo
        Path once = configRoot.resolve("jar_once.txt");
        assertThat(once).exists();

        assertThat(report.countByType(GenerationAction.Type.TEMPLATE)).isEqualTo(1);
        assertThat(report.countByType(GenerationAction.Type.COPY)).isGreaterThanOrEqualTo(3);
    }

    @Test
    void writeOnceFromJarPreserved(@TempDir Path configRoot) throws Exception {
        Files.writeString(configRoot.resolve("chibiforge.xcfg"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <chibiforgeConfiguration
                    xmlns="http://chibiforge/schema/config"
                    toolVersion="1.0.0"
                    schemaVersion="1.0">
                  <targets><target id="default"/></targets>
                  <components>
                    <component id="test.jarcomp">
                      <settings><message>test</message></settings>
                    </component>
                  </components>
                </chibiforgeConfiguration>
                """);

        GenerationContext ctx = new GenerationContext(configRoot, "default", false, false);
        GeneratorEngine engine = new GeneratorEngine();

        // First run
        engine.generate(ctx, null, pluginsRoot);
        Path once = configRoot.resolve("jar_once.txt");
        Files.writeString(once, "user modified");

        // Second run
        GenerationReport report2 = engine.generate(ctx, null, pluginsRoot);
        assertThat(Files.readString(once)).isEqualTo("user modified");
        assertThat(report2.countByType(GenerationAction.Type.SKIP)).isGreaterThanOrEqualTo(1);
    }

    @Test
    void dryRunFromJar(@TempDir Path configRoot) throws Exception {
        Files.writeString(configRoot.resolve("chibiforge.xcfg"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <chibiforgeConfiguration
                    xmlns="http://chibiforge/schema/config"
                    toolVersion="1.0.0"
                    schemaVersion="1.0">
                  <targets><target id="default"/></targets>
                  <components>
                    <component id="test.jarcomp">
                      <settings><message>test</message></settings>
                    </component>
                  </components>
                </chibiforgeConfiguration>
                """);

        GenerationContext ctx = new GenerationContext(configRoot, "default", true, false);
        GenerationReport report = new GeneratorEngine().generate(ctx, null, pluginsRoot);

        assertThat(configRoot.resolve("generated")).doesNotExist();
        assertThat(report.getActions()).isNotEmpty();
    }
}
