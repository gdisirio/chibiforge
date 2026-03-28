package org.chibios.chibiforge.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.chibios.chibiforge.component.ComponentDefinition;
import org.chibios.chibiforge.component.ResourceDef;
import org.chibios.chibiforge.container.ComponentContent;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads resource files (XML and JSON) declared in a component definition.
 * Resources become top-level FMPP variables.
 */
public class ResourceLoader {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * Load all resources declared in a component definition.
     * @return map of resource ID -> parsed resource (Document for XML, JsonNode for JSON)
     */
    public Map<String, Object> loadResources(ComponentDefinition def, ComponentContent content) throws Exception {
        Map<String, Object> resources = new LinkedHashMap<>();

        for (ResourceDef resDef : def.getResources()) {
            String file = resDef.getFile();
            Object parsed;

            try (InputStream is = content.open(file)) {
                if (file.endsWith(".json")) {
                    parsed = JSON_MAPPER.readTree(is);
                } else {
                    // Default to XML
                    parsed = parseXml(is);
                }
            }

            resources.put(resDef.getId(), parsed);
        }

        return resources;
    }

    private Document parseXml(InputStream is) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(is);
    }
}
