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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Abstracts file listing and opening within the component/ subtree.
 * Implementations handle filesystem and JAR sources transparently.
 */
public interface ComponentContent {

    /**
     * Opens a file relative to the component/ directory.
     * @param relativePath e.g. "cfg/mcuconf.h.ftl", "resources/limits.xml"
     * @return an input stream for the file
     */
    InputStream open(String relativePath) throws IOException;

    /**
     * Lists files under a prefix relative to the component/ directory.
     * @param prefix e.g. "cfg/", "source/", "resources/"
     * @return list of relative paths (relative to component/)
     */
    List<String> list(String prefix) throws IOException;

    /**
     * Checks if a file exists relative to the component/ directory.
     */
    boolean exists(String relativePath);
}
