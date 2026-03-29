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
 * A resource declaration from a component definition.
 * Maps to {@code <resource id="..." file="..."/>} in schema.xml.
 */
public class ResourceDef {
    private final String id;
    private final String file;

    public ResourceDef(String id, String file) {
        this.id = id;
        this.file = file;
    }

    public String getId() { return id; }
    public String getFile() { return file; }

    @Override
    public String toString() {
        return "ResourceDef{id='" + id + "', file='" + file + "'}";
    }
}
