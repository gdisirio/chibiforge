package org.chibios.chibiforge.container;

import org.chibios.chibiforge.component.ComponentDefinition;
import org.chibios.chibiforge.component.ComponentDefinitionParser;

import java.io.InputStream;
import java.nio.file.Path;

/**
 * A component container backed by a filesystem directory.
 * Expected layout: containerRoot/component/schema.xml
 */
public class FilesystemContainer implements ComponentContainer {

    private final Path containerRoot;
    private final FilesystemContent content;
    private ComponentDefinition cachedDefinition;

    public FilesystemContainer(Path containerRoot) {
        this.containerRoot = containerRoot;
        this.content = new FilesystemContent(containerRoot.resolve("component"));
    }

    @Override
    public String getId() {
        try {
            return loadDefinition().getId();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load component ID from " + containerRoot, e);
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

    public Path getContainerRoot() { return containerRoot; }
}
