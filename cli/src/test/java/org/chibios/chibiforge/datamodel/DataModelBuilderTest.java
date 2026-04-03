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
                "generated/" + IdNormalizer.normalize(entry.getComponentId()) + "/",
                Map.of(entry.getComponentId(), "generated/" + IdNormalizer.normalize(entry.getComponentId()) + "/"),
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
    void globalVariableExists() {
        assertThat(dataModel).containsKey("global");
        assertThat(dataModel.get("global")).isInstanceOf(NodeModel.class);
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
        String templateStr = "${components.org_chibios_chibiforge_components_hal_stm32f4xx.initialization_settings.vdd}";
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

    @Test
    void globalPathsAreExposed() throws Exception {
        String templateStr = "${global.absolute_configuration_path}|${global.component_path}|"
                + "<#list global.component_paths.path as p>${p}<#if p_has_next>,</#if></#list>";
        Template template = new Template("test", templateStr, fmConfig);

        StringWriter out = new StringWriter();
        template.process(dataModel, out);
        assertThat(out.toString()).contains("/").contains("generated/org_chibios_chibiforge_components_hal_stm32f4xx/");
    }

    // --- Multi-target tests ---

    private static Map<String, Object> buildMultiTargetModel(String target) throws Exception {
        ConfigLoader loader = new ConfigLoader();
        ChibiForgeConfiguration config;
        try (InputStream is = DataModelBuilderTest.class.getResourceAsStream("/fixtures/configs/multitarget.xcfg")) {
            config = loader.load(is);
        }
        ComponentConfigEntry entry = config.getComponents().get(0);
        Map<String, ComponentConfigEntry> allConfigs = new LinkedHashMap<>();
        allConfigs.put(entry.getComponentId(), entry);

        return new DataModelBuilder().buildDataModel(
                entry.getComponentId(), entry, allConfigs,
                "generated/" + IdNormalizer.normalize(entry.getComponentId()) + "/",
                Map.of(entry.getComponentId(), "generated/" + IdNormalizer.normalize(entry.getComponentId()) + "/"),
                Map.of(), Path.of("/tmp"), target);
    }

    private static String eval(Map<String, Object> model, String expr) throws Exception {
        Template t = new Template("test", "${" + expr + "}", fmConfig);
        StringWriter out = new StringWriter();
        t.process(model, out);
        return out.toString().trim();
    }

    @Test
    void multiTarget_defaultTarget_usesDefaultValues() throws Exception {
        Map<String, Object> model = buildMultiTargetModel("default");
        assertThat(eval(model, "doc.settings.greeting")).isEqualTo("Hello");
        assertThat(eval(model, "doc.settings.count")).isEqualTo("3");
        assertThat(eval(model, "doc.settings.enabled")).isEqualTo("true");
    }

    @Test
    void multiTarget_debugTarget_usesOverrides() throws Exception {
        Map<String, Object> model = buildMultiTargetModel("debug");
        assertThat(eval(model, "doc.settings.greeting")).isEqualTo("Hello"); // single-target, unchanged
        assertThat(eval(model, "doc.settings.count")).isEqualTo("10");       // debug override
        assertThat(eval(model, "doc.settings.enabled")).isEqualTo("false");  // debug override
    }

    @Test
    void multiTarget_releaseTarget_usesOverrideOrFallback() throws Exception {
        Map<String, Object> model = buildMultiTargetModel("release");
        assertThat(eval(model, "doc.settings.greeting")).isEqualTo("Hello"); // single-target
        assertThat(eval(model, "doc.settings.count")).isEqualTo("1");        // release override
        assertThat(eval(model, "doc.settings.enabled")).isEqualTo("true");   // no release override, falls back to default
    }

    @Test
    void multiTarget_componentsAlsoResolved() throws Exception {
        Map<String, Object> model = buildMultiTargetModel("debug");
        assertThat(eval(model, "components.test_hello.settings.count")).isEqualTo("10");
    }
}
