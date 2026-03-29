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

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses a chibiforge.xcfg configuration file.
 */
public class ConfigLoader {

    private static final String NS = "http://chibiforge/schema/config";

    public ChibiForgeConfiguration load(Path configPath) throws Exception {
        try (InputStream is = Files.newInputStream(configPath)) {
            return load(is);
        }
    }

    public ChibiForgeConfiguration load(InputStream input) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(input);

        Element root = doc.getDocumentElement();
        if (!"chibiforgeConfiguration".equals(root.getLocalName())) {
            throw new IllegalArgumentException(
                    "Expected root element <chibiforgeConfiguration>, got <" + root.getLocalName() + ">");
        }

        String toolVersion = root.getAttribute("toolVersion");
        String schemaVersion = root.getAttribute("schemaVersion");

        List<String> targets = parseTargets(root);
        List<ComponentConfigEntry> components = parseComponents(root);

        return new ChibiForgeConfiguration(toolVersion, schemaVersion, targets, components);
    }

    private List<String> parseTargets(Element root) {
        List<String> targets = new ArrayList<>();
        NodeList targetsNodes = root.getElementsByTagNameNS(NS, "targets");
        if (targetsNodes.getLength() == 0) {
            // Also try without namespace for flexibility
            targetsNodes = root.getElementsByTagName("targets");
        }
        if (targetsNodes.getLength() > 0) {
            Element targetsEl = (Element) targetsNodes.item(0);
            NodeList targetNodes = targetsEl.getElementsByTagNameNS(NS, "target");
            if (targetNodes.getLength() == 0) {
                targetNodes = targetsEl.getElementsByTagName("target");
            }
            for (int i = 0; i < targetNodes.getLength(); i++) {
                Element targetEl = (Element) targetNodes.item(i);
                String id = targetEl.getAttribute("id");
                if (id != null && !id.isEmpty()) {
                    targets.add(id);
                }
            }
        }
        if (targets.isEmpty()) {
            targets.add("default");
        }
        return targets;
    }

    private List<ComponentConfigEntry> parseComponents(Element root) {
        List<ComponentConfigEntry> components = new ArrayList<>();
        NodeList componentsNodes = root.getElementsByTagNameNS(NS, "components");
        if (componentsNodes.getLength() == 0) {
            componentsNodes = root.getElementsByTagName("components");
        }
        if (componentsNodes.getLength() > 0) {
            Element componentsEl = (Element) componentsNodes.item(0);
            NodeList componentNodes = componentsEl.getElementsByTagNameNS(NS, "component");
            if (componentNodes.getLength() == 0) {
                componentNodes = componentsEl.getElementsByTagName("component");
            }
            for (int i = 0; i < componentNodes.getLength(); i++) {
                Element componentEl = (Element) componentNodes.item(i);
                String id = componentEl.getAttribute("id");
                if (id == null || id.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Missing required attribute 'id' on <component> element");
                }
                components.add(new ComponentConfigEntry(id, componentEl));
            }
        }
        return components;
    }
}
