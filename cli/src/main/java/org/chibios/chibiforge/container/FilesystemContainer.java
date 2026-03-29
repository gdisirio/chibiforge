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
