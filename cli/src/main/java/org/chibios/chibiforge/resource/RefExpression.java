package org.chibios.chibiforge.resource;

import com.fasterxml.jackson.databind.JsonNode;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses and resolves {@code @ref:} expressions.
 * Syntax: {@code @ref:<resourceId>/<xpath>}
 */
public class RefExpression {

    private static final String PREFIX = "@ref:";

    /**
     * Check if a value is a @ref: expression.
     */
    public static boolean isRef(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    /**
     * Resolve a @ref: expression against loaded resources.
     * @param expression the full @ref: expression string
     * @param resources map of resource ID -> parsed resource (Document or JsonNode)
     * @return resolved value as a string, or a CSV if multiple nodes match
     */
    public static String resolve(String expression, Map<String, Object> resources) {
        if (!isRef(expression)) {
            return expression;
        }

        String path = expression.substring(PREFIX.length());
        int slashIdx = path.indexOf('/');
        if (slashIdx < 0) {
            throw new IllegalArgumentException("Invalid @ref: expression (missing path): " + expression);
        }

        String resourceId = path.substring(0, slashIdx);
        String xpath = path.substring(slashIdx); // Keep leading /

        Object resource = resources.get(resourceId);
        if (resource == null) {
            throw new IllegalArgumentException("Resource '" + resourceId + "' not found for @ref: " + expression);
        }

        if (resource instanceof Document doc) {
            return resolveXml(doc, xpath, expression);
        } else if (resource instanceof JsonNode jsonNode) {
            return resolveJson(jsonNode, xpath, expression);
        } else {
            throw new IllegalArgumentException("Unsupported resource type for @ref: " + resource.getClass());
        }
    }

    private static String resolveXml(Document doc, String xpath, String expression) {
        try {
            XPath xp = XPathFactory.newInstance().newXPath();

            // Try as node set first (for multi-value like enum lists)
            NodeList nodes = (NodeList) xp.evaluate(xpath, doc, XPathConstants.NODESET);
            if (nodes.getLength() == 0) {
                throw new IllegalArgumentException(
                        "XPath '" + xpath + "' returned no results for @ref: " + expression);
            }
            if (nodes.getLength() == 1) {
                return nodes.item(0).getTextContent().trim();
            }
            // Multiple results: join as CSV
            List<String> values = new ArrayList<>();
            for (int i = 0; i < nodes.getLength(); i++) {
                values.add(nodes.item(i).getTextContent().trim());
            }
            return String.join(",", values);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to evaluate XPath for @ref: " + expression, e);
        }
    }

    private static String resolveJson(JsonNode root, String path, String expression) {
        // Convert XPath-like path to JSON navigation: /a/b/@c -> navigate a.b.c
        String[] segments = path.split("/");
        JsonNode current = root;

        for (String segment : segments) {
            if (segment.isEmpty()) continue;
            String key = segment.startsWith("@") ? segment.substring(1) : segment;
            if (current.has(key)) {
                current = current.get(key);
            } else {
                throw new IllegalArgumentException(
                        "JSON path segment '" + key + "' not found for @ref: " + expression);
            }
        }

        if (current.isArray()) {
            List<String> values = new ArrayList<>();
            for (JsonNode node : current) {
                values.add(node.isTextual() ? node.asText() : node.toString());
            }
            return String.join(",", values);
        }

        return current.isTextual() ? current.asText() : current.toString();
    }
}
