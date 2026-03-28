package org.chibios.chibiforge.generator;

import org.chibios.chibiforge.config.ChibiForgeConfiguration;
import org.chibios.chibiforge.config.ComponentConfigEntry;
import org.chibios.chibiforge.config.ConfigLoader;
import org.chibios.chibiforge.container.FilesystemContent;
import org.chibios.chibiforge.datamodel.DataModelBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateProcessorTest {

    @Test
    void processesClassicTemplate(@TempDir Path tempDir) throws Exception {
        Path componentDir = Paths.get(
                getClass().getResource("/fixtures/simple-component/component").toURI());
        FilesystemContent content = new FilesystemContent(componentDir);

        // Load config
        ConfigLoader loader = new ConfigLoader();
        ChibiForgeConfiguration config;
        try (InputStream is = getClass().getResourceAsStream("/fixtures/configs/simple.xcfg")) {
            config = loader.load(is);
        }

        ComponentConfigEntry entry = config.getComponents().get(0);
        Map<String, ComponentConfigEntry> allConfigs = new LinkedHashMap<>();
        allConfigs.put(entry.getComponentId(), entry);

        // Build data model
        DataModelBuilder dmBuilder = new DataModelBuilder();
        Map<String, Object> dataModel = dmBuilder.buildDataModel(
                entry.getComponentId(), entry, allConfigs,
                Map.of(), tempDir, "default");

        // Process templates
        GenerationContext ctx = new GenerationContext(tempDir, "default", false, false);
        GenerationReport report = new GenerationReport();

        TemplateProcessor processor = new TemplateProcessor();
        processor.processTemplates(entry.getComponentId(), content, dataModel, ctx, report);

        // Verify output
        Path outputFile = tempDir.resolve("generated/output.h");
        assertThat(outputFile).exists();

        String outputContent = Files.readString(outputFile);
        assertThat(outputContent).contains("#define VDD_VALUE 300");
        assertThat(outputContent).contains("#define CLOCK_SOURCE \"PLL\"");
        assertThat(report.countByType(GenerationAction.Type.TEMPLATE)).isEqualTo(1);
    }

    @Test
    void dryRunDoesNotWriteTemplates(@TempDir Path tempDir) throws Exception {
        Path componentDir = Paths.get(
                getClass().getResource("/fixtures/simple-component/component").toURI());
        FilesystemContent content = new FilesystemContent(componentDir);

        ConfigLoader loader = new ConfigLoader();
        ChibiForgeConfiguration config;
        try (InputStream is = getClass().getResourceAsStream("/fixtures/configs/simple.xcfg")) {
            config = loader.load(is);
        }

        ComponentConfigEntry entry = config.getComponents().get(0);
        Map<String, ComponentConfigEntry> allConfigs = new LinkedHashMap<>();
        allConfigs.put(entry.getComponentId(), entry);

        DataModelBuilder dmBuilder = new DataModelBuilder();
        Map<String, Object> dataModel = dmBuilder.buildDataModel(
                entry.getComponentId(), entry, allConfigs,
                Map.of(), tempDir, "default");

        GenerationContext ctx = new GenerationContext(tempDir, "default", true, false);
        GenerationReport report = new GenerationReport();

        TemplateProcessor processor = new TemplateProcessor();
        processor.processTemplates(entry.getComponentId(), content, dataModel, ctx, report);

        assertThat(tempDir.resolve("generated/output.h")).doesNotExist();
        assertThat(report.countByType(GenerationAction.Type.TEMPLATE)).isEqualTo(1);
    }
}
