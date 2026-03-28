package org.chibios.chibiforge.datamodel;

import freemarker.ext.dom.NodeModel;
import org.chibios.chibiforge.config.ComponentConfigEntry;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the FMPP-compatible data model for template processing.
 *
 * Top-level variables:
 * - doc: current component's resolved configuration (wrapped XML)
 * - components: all components' resolved configurations keyed by normalized ID
 * - configuration: metadata (root, generatedRoot, target)
 * - One variable per resource
 */
public class DataModelBuilder {

    /**
     * Build the complete data model for one component's template processing.
     *
     * @param componentId the current component's ID
     * @param configEntry the current component's config from xcfg
     * @param allConfigs all component configs from xcfg, keyed by component ID
     * @param resources loaded resources for this component (resource ID -> Document/JsonNode)
     * @param configRoot the configuration root directory
     * @param target the active target name
     * @return data model map suitable for FMPP/FreeMarker
     */
    public Map<String, Object> buildDataModel(
            String componentId,
            ComponentConfigEntry configEntry,
            Map<String, ComponentConfigEntry> allConfigs,
            Map<String, Object> resources,
            Path configRoot,
            String target) throws Exception {

        Map<String, Object> dataModel = new LinkedHashMap<>();

        // doc: current component's config
        dataModel.put("doc", buildDoc(configEntry));

        // components: all configs keyed by normalized ID
        dataModel.put("components", buildComponents(allConfigs));

        // configuration: metadata
        dataModel.put("configuration", buildConfiguration(configRoot, target));

        // Resource variables
        for (Map.Entry<String, Object> entry : resources.entrySet()) {
            Object resource = entry.getValue();
            if (resource instanceof Document xmlDoc) {
                dataModel.put(entry.getKey(), NodeModel.wrap(xmlDoc));
            } else {
                // JSON resources: store as-is for now (FreeMarker can handle via BeansWrapper)
                dataModel.put(entry.getKey(), resource);
            }
        }

        return dataModel;
    }

    private Object buildDoc(ComponentConfigEntry configEntry) throws Exception {
        Document doc = createDocument();
        Element docRoot = doc.createElement("doc");
        doc.appendChild(docRoot);

        // Import all children, stripping namespaces for clean FreeMarker access
        Element sourceEl = configEntry.getConfigElement();
        copyChildrenStrippingNamespace(sourceEl, docRoot, doc);

        // Wrap the root element directly so template access is doc.section.property
        return NodeModel.wrap(docRoot);
    }

    private Object buildComponents(Map<String, ComponentConfigEntry> allConfigs) throws Exception {
        Document doc = createDocument();
        Element componentsRoot = doc.createElement("components");
        doc.appendChild(componentsRoot);

        for (Map.Entry<String, ComponentConfigEntry> entry : allConfigs.entrySet()) {
            String normalizedId = IdNormalizer.normalize(entry.getKey());
            Element compEl = doc.createElement(normalizedId);
            componentsRoot.appendChild(compEl);

            Element sourceEl = entry.getValue().getConfigElement();
            copyChildrenStrippingNamespace(sourceEl, compEl, doc);
        }

        return NodeModel.wrap(componentsRoot);
    }

    private Object buildConfiguration(Path configRoot, String target) throws Exception {
        Document doc = createDocument();
        Element root = doc.createElement("configuration");
        doc.appendChild(root);

        Element rootEl = doc.createElement("root");
        rootEl.setTextContent(configRoot.toAbsolutePath().toString());
        root.appendChild(rootEl);

        Element genRootEl = doc.createElement("generatedRoot");
        genRootEl.setTextContent(configRoot.resolve("generated").toAbsolutePath().toString());
        root.appendChild(genRootEl);

        Element targetEl = doc.createElement("target");
        targetEl.setTextContent(target);
        root.appendChild(targetEl);

        return NodeModel.wrap(root);
    }

    /**
     * Recursively copies child nodes from source to target, stripping XML namespaces.
     * This ensures FreeMarker can access elements by simple names (e.g., doc.section.property).
     */
    private void copyChildrenStrippingNamespace(Element source, Element target, Document doc) {
        NodeList children = source.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element srcEl) {
                String localName = srcEl.getLocalName() != null ? srcEl.getLocalName() : srcEl.getTagName();
                Element newEl = doc.createElement(localName);

                // Copy attributes (without namespace)
                NamedNodeMap attrs = srcEl.getAttributes();
                for (int j = 0; j < attrs.getLength(); j++) {
                    Node attr = attrs.item(j);
                    String attrName = attr.getLocalName() != null ? attr.getLocalName() : attr.getNodeName();
                    // Skip xmlns attributes
                    if (!"xmlns".equals(attrName) && !attr.getNodeName().startsWith("xmlns:")) {
                        newEl.setAttribute(attrName, attr.getNodeValue());
                    }
                }

                copyChildrenStrippingNamespace(srcEl, newEl, doc);
                target.appendChild(newEl);
            } else if (child instanceof Text) {
                target.appendChild(doc.importNode(child, true));
            }
        }
    }

    private Document createDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.newDocument();
    }
}
