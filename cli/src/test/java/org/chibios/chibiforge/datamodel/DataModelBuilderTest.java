package org.chibios.chibiforge.datamodel;

import freemarker.ext.dom.NodeModel;
import freemarker.template.*;
import org.chibios.chibiforge.config.ChibiForgeConfiguration;
import org.chibios.chibiforge.config.ComponentConfigEntry;
import org.chibios.chibiforge.config.ConfigLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DataModelBuilderTest {

    private static Map<String, Object> dataModel;
    private static Configuration fmConfig;

    @BeforeAll
    static void setup(@TempDir Path tempDir) throws Exception {
        ConfigLoader loader = new ConfigLoader();
        ChibiForgeConfiguration config;
        try (InputStream is = DataModelBuilderTest.class.getResourceAsStream("/fixtures/configs/simple.xcfg")) {
            config = loader.load(is);
        }

        ComponentConfigEntry entry = config.getComponents().get(0);
        Map<String, ComponentConfigEntry> allConfigs = new LinkedHashMap<>();
        allConfigs.put(entry.getComponentId(), entry);

        DataModelBuilder builder = new DataModelBuilder();
        dataModel = builder.buildDataModel(
                entry.getComponentId(), entry, allConfigs,
                Map.of(), tempDir, "default");

        fmConfig = new Configuration(Configuration.VERSION_2_3_32);
        fmConfig.setDefaultEncoding("UTF-8");
    }

    @Test
    void docVariableExists() {
        assertThat(dataModel).containsKey("doc");
        assertThat(dataModel.get("doc")).isInstanceOf(NodeModel.class);
    }

    @Test
    void componentsVariableExists() {
        assertThat(dataModel).containsKey("components");
    }

    @Test
    void configurationVariableExists() {
        assertThat(dataModel).containsKey("configuration");
    }

    @Test
    void docAccessViaCTemplate() throws Exception {
        // Test that FreeMarker can access doc.initialization_settings.vdd
        String templateStr = "${doc.initialization_settings.vdd}";
        Template template = new Template("test", templateStr, fmConfig);

        StringWriter out = new StringWriter();
        template.process(dataModel, out);
        assertThat(out.toString().trim()).isEqualTo("300");
    }

    @Test
    void docAccessBoolProperty() throws Exception {
        String templateStr = "${doc.initialization_settings.do_not_init}";
        Template template = new Template("test", templateStr, fmConfig);

        StringWriter out = new StringWriter();
        template.process(dataModel, out);
        assertThat(out.toString().trim()).isEqualTo("false");
    }

    @Test
    void componentsAccessViaCTemplate() throws Exception {
        String templateStr = "${components.org_chibios_hal_stm32f4xx.initialization_settings.vdd}";
        Template template = new Template("test", templateStr, fmConfig);

        StringWriter out = new StringWriter();
        template.process(dataModel, out);
        assertThat(out.toString().trim()).isEqualTo("300");
    }

    @Test
    void configurationTarget() throws Exception {
        String templateStr = "${configuration.target}";
        Template template = new Template("test", templateStr, fmConfig);

        StringWriter out = new StringWriter();
        template.process(dataModel, out);
        assertThat(out.toString().trim()).isEqualTo("default");
    }
}
