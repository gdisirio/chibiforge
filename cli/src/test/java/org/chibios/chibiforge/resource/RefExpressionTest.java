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

import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefExpressionTest {

    @Test
    void isRef_true() {
        assertThat(RefExpression.isRef("@ref:limits/adc/@max")).isTrue();
    }

    @Test
    void isRef_false() {
        assertThat(RefExpression.isRef("300")).isFalse();
        assertThat(RefExpression.isRef(null)).isFalse();
        assertThat(RefExpression.isRef("")).isFalse();
    }

    @Test
    void nonRefReturnsAsIs() {
        assertThat(RefExpression.resolve("hello", Map.of())).isEqualTo("hello");
    }

    @Test
    void resolvesXmlAttribute() throws Exception {
        Document doc = parseXml("<root><adc max_channels=\"16\"/></root>");
        Map<String, Object> resources = Map.of("limits", doc);

        String result = RefExpression.resolve("@ref:limits/root/adc/@max_channels", resources);
        assertThat(result).isEqualTo("16");
    }

    @Test
    void resolvesXmlTextContent() throws Exception {
        Document doc = parseXml("<root><value>42</value></root>");
        Map<String, Object> resources = Map.of("data", doc);

        String result = RefExpression.resolve("@ref:data/root/value", resources);
        assertThat(result).isEqualTo("42");
    }

    @Test
    void missingResource_throws() {
        assertThatThrownBy(() -> RefExpression.resolve("@ref:missing/path", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void invalidExpression_throws() {
        assertThatThrownBy(() -> RefExpression.resolve("@ref:noslash", Map.of("noslash", "val")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing path");
    }

    private static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
}
