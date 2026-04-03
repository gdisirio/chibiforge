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

import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;

/**
 * A component container that provides access to a component's definition and content.
 * Implementations handle filesystem directories and JAR files.
 */
public interface ComponentContainer {

    /**
     * Returns the component ID (from schema.xml).
     */
    String getId();

    /**
     * Parses and returns the component definition from schema.xml.
     */
    ComponentDefinition loadDefinition() throws Exception;

    /**
     * Returns access to the component/ subtree content.
     */
    ComponentContent getComponentContent();

    /**
     * Lists bundled preset files under component/presets/.
     * Returned paths are relative to the presets/ directory.
     */
    default List<String> listBundledPresets() throws IOException {
        return getComponentContent().list("presets/").stream()
                .filter(path -> path.startsWith("presets/"))
                .map(path -> path.substring("presets/".length()))
                .filter(path -> !path.isBlank())
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    /**
     * Opens a bundled preset relative to component/presets/.
     */
    default InputStream openBundledPreset(String relativePath) throws IOException {
        if (relativePath == null || relativePath.isBlank()) {
            throw new IllegalArgumentException("Preset path must not be blank");
        }
        if (relativePath.startsWith("/") || relativePath.contains("..")) {
            throw new IllegalArgumentException("Preset path must stay within component/presets/: " + relativePath);
        }
        return getComponentContent().open("presets/" + relativePath);
    }
}
