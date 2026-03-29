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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * ComponentContent backed by a JAR file.
 * All paths are relative to the "component/" prefix inside the JAR.
 */
public class JarContent implements ComponentContent {

    private final JarFile jarFile;
    private static final String BASE = "component/";

    /**
     * @param jarFile the opened JAR file
     */
    public JarContent(JarFile jarFile) {
        this.jarFile = jarFile;
    }

    @Override
    public InputStream open(String relativePath) throws IOException {
        String entryName = BASE + relativePath;
        JarEntry entry = jarFile.getJarEntry(entryName);
        if (entry == null) {
            throw new IOException("Entry not found in JAR: " + entryName);
        }
        return jarFile.getInputStream(entry);
    }

    @Override
    public List<String> list(String prefix) throws IOException {
        String fullPrefix = BASE + prefix;
        List<String> result = new ArrayList<>();
        Enumeration<JarEntry> entries = jarFile.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (!entry.isDirectory() && entry.getName().startsWith(fullPrefix)) {
                // Return path relative to component/ (strip BASE prefix)
                result.add(entry.getName().substring(BASE.length()));
            }
        }
        return result;
    }

    @Override
    public boolean exists(String relativePath) {
        return jarFile.getJarEntry(BASE + relativePath) != null;
    }

    public JarFile getJarFile() { return jarFile; }
}
