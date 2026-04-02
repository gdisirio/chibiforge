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

package org.chibios.chibiforge.ui.io;

import org.w3c.dom.*;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Writes a chibiforge.xcfg configuration DOM to disk as formatted XML.
 *
 * Per spec §6.2.2, type="text" property values are always serialized
 * using a single CDATA section, regardless of content.
 */
public class XcfgWriter {

    /**
     * Save the configuration DOM to a file.
     *
     * @param configRootElement the document root element
     * @param outputPath the file to write
     * @param textPropertyNames set of property element names that are type="text"
     *                          (these are always wrapped in CDATA)
     */
    public void save(Element configRootElement, Path outputPath,
                     Set<String> textPropertyNames) throws Exception {
        Document doc = configRootElement.getOwnerDocument();

        stripWhitespaceNodes(doc.getDocumentElement());
        convertTextToCdata(doc.getDocumentElement(), textPropertyNames);

        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));

        Files.writeString(outputPath, writer.toString());
    }

    /**
     * Recursively remove whitespace-only text nodes from the DOM.
     */
    private void stripWhitespaceNodes(Node node) {
        NodeList children = node.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                if (child.getTextContent().trim().isEmpty()) {
                    node.removeChild(child);
                }
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                stripWhitespaceNodes(child);
            }
        }
    }

    /**
     * Convert type="text" property elements to use CDATA sections.
     * Only elements whose tag name is in textPropertyNames are converted.
     */
    private void convertTextToCdata(Element element, Set<String> textPropertyNames) {
        NodeList children = element.getChildNodes();

        // Check if this is a leaf element matching a text property name
        String tagName = element.getLocalName() != null ? element.getLocalName() : element.getTagName();
        if (textPropertyNames.contains(tagName) && isLeafElement(element)) {
            String text = element.getTextContent();
            if (text != null && !text.isEmpty()) {
                // Remove existing text/CDATA nodes
                for (int i = children.getLength() - 1; i >= 0; i--) {
                    Node child = children.item(i);
                    if (child.getNodeType() == Node.TEXT_NODE
                            || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                        element.removeChild(child);
                    }
                }
                CDATASection cdata = element.getOwnerDocument().createCDATASection(text);
                element.appendChild(cdata);
            }
            return;
        }

        // Recurse into child elements
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child) {
                convertTextToCdata(child, textPropertyNames);
            }
        }
    }

    private boolean isLeafElement(Element element) {
        NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                return false;
            }
        }
        return true;
    }
}
