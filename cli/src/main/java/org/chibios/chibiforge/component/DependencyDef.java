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

package org.chibios.chibiforge.component;

/**
 * A hard component dependency declaration from schema.xml.
 * Maps to {@code <component id="..." version="..." minVersion="..."/>} inside {@code <depends>}.
 */
public class DependencyDef {
    private final String id;
    private final String version;
    private final String minVersion;

    public DependencyDef(String id, String version, String minVersion) {
        this.id = id;
        this.version = version;
        this.minVersion = minVersion;
    }

    public String getId() { return id; }
    public String getVersion() { return version; }
    public String getMinVersion() { return minVersion; }

    public boolean hasExactVersion() {
        return version != null && !version.isBlank();
    }

    public boolean hasMinVersion() {
        return minVersion != null && !minVersion.isBlank();
    }

    @Override
    public String toString() {
        return "DependencyDef{id='" + id + "', version='" + version + "', minVersion='" + minVersion + "'}";
    }
}
