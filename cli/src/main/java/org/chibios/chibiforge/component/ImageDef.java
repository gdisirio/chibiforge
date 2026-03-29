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
 * An image definition from a component schema.
 * Maps to {@code <image>} elements in schema.xml.
 */
public class ImageDef {
    private final String file;
    private final String align;
    private final String text;

    public ImageDef(String file, String align, String text) {
        this.file = file;
        this.align = align;
        this.text = text;
    }

    public String getFile() { return file; }
    public String getAlign() { return align; }
    public String getText() { return text; }

    @Override
    public String toString() {
        return "ImageDef{file='" + file + "', align='" + align + "'}";
    }
}
