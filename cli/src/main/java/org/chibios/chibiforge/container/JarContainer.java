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

package org.chibios.chibiforge.container;

import org.chibios.chibiforge.component.ComponentDefinition;
import org.chibios.chibiforge.component.ComponentDefinitionParser;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * A component container backed by a plugin JAR file.
 * Validates that plugin.xml contains the ChibiForge extension point marker.
 */
public class JarContainer implements ComponentContainer {

    private static final String EXTENSION_POINT = "org.chibios.chibiforge.component";

    private final Path jarPath;
    private final JarFile jarFile;
    private final JarContent content;
    private ComponentDefinition cachedDefinition;

    private JarContainer(Path jarPath, JarFile jarFile) {
        this.jarPath = jarPath;
        this.jarFile = jarFile;
        this.content = new JarContent(jarFile);
    }

    /**
     * Open a JAR file and validate it is a ChibiForge component container.
     * @return the container, or null if the JAR is not a ChibiForge plugin
     */
    public static JarContainer openIfValid(Path jarPath) throws IOException {
        JarFile jarFile = new JarFile(jarPath.toFile());

        // Check for plugin.xml with ChibiForge extension point
        JarEntry pluginEntry = jarFile.getJarEntry("plugin.xml");
        if (pluginEntry == null) {
            jarFile.close();
            return null;
        }

        try (InputStream is = jarFile.getInputStream(pluginEntry)) {
            if (!hasExtensionPoint(is)) {
                jarFile.close();
                return null;
            }
        } catch (Exception e) {
            jarFile.close();
            return null;
        }

        // Check for component/schema.xml
        if (jarFile.getJarEntry("component/schema.xml") == null) {
            jarFile.close();
            return null;
        }

        return new JarContainer(jarPath, jarFile);
    }

    private static boolean hasExtensionPoint(InputStream pluginXml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(pluginXml);

        NodeList extensions = doc.getElementsByTagName("extension");
        for (int i = 0; i < extensions.getLength(); i++) {
            Element ext = (Element) extensions.item(i);
            if (EXTENSION_POINT.equals(ext.getAttribute("point"))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getId() {
        try {
            return loadDefinition().getId();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load component ID from " + jarPath, e);
        }
    }

    @Override
    public ComponentDefinition loadDefinition() throws Exception {
        if (cachedDefinition == null) {
            ComponentDefinitionParser parser = new ComponentDefinitionParser();
            try (InputStream is = content.open("schema.xml")) {
                cachedDefinition = parser.parse(is);
            }
        }
        return cachedDefinition;
    }

    @Override
    public ComponentContent getComponentContent() {
        return content;
    }

    public Path getJarPath() { return jarPath; }
}
