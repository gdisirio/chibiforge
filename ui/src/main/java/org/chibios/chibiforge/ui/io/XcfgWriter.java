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

/**
 * Writes a chibiforge.xcfg configuration DOM to disk as formatted XML.
 *
 * Per spec §6.2.2, text property values containing XML-sensitive characters
 * are serialized using CDATA sections.
 */
public class XcfgWriter {

    /**
     * Save the configuration DOM to a file.
     */
    public void save(Element configRootElement, Path outputPath) throws Exception {
        Document doc = configRootElement.getOwnerDocument();

        // Strip whitespace-only text nodes so the transformer's indentation is clean
        stripWhitespaceNodes(doc.getDocumentElement());

        // Convert leaf elements with XML-sensitive content to use CDATA
        convertToCdata(doc.getDocumentElement());

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
     * Preserves text content in leaf elements (actual property values).
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
     * Convert leaf elements whose text content contains XML-sensitive characters
     * (&lt;, &gt;, &amp;) to use a CDATA section instead of escaped text.
     * This preserves source code snippets verbatim in the xcfg file.
     */
    private void convertToCdata(Element element) {
        NodeList children = element.getChildNodes();

        // Check if this is a leaf element (only text/CDATA children, no child elements)
        boolean hasChildElements = false;
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == Node.ELEMENT_NODE) {
                hasChildElements = true;
                break;
            }
        }

        if (hasChildElements) {
            // Recurse into child elements
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element child) {
                    convertToCdata(child);
                }
            }
        } else {
            // Leaf element — check if content needs CDATA
            String text = element.getTextContent();
            if (text != null && needsCdata(text)) {
                // Remove existing text nodes
                for (int i = children.getLength() - 1; i >= 0; i--) {
                    Node child = children.item(i);
                    if (child.getNodeType() == Node.TEXT_NODE
                            || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                        element.removeChild(child);
                    }
                }
                // Add a single CDATA section
                CDATASection cdata = element.getOwnerDocument().createCDATASection(text);
                element.appendChild(cdata);
            }
        }
    }

    /**
     * Check if text content needs CDATA wrapping.
     */
    private boolean needsCdata(String text) {
        return text.contains("<") || text.contains(">") || text.contains("&");
    }
}
