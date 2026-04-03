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

package org.chibios.chibiforge.preset;

import org.chibios.chibiforge.component.PropertyDef;

import java.util.List;

/**
 * A preset property entry, either scalar-valued or list-valued.
 */
public record PresetProperty(String name, PropertyDef.Type type, String value, List<PresetItem> items) {

    public PresetProperty {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Preset property name must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Preset property type must not be null");
        }
        items = items != null ? List.copyOf(items) : List.of();

        if (type == PropertyDef.Type.LIST) {
            if (value != null) {
                throw new IllegalArgumentException(
                        "Preset list property '" + name + "' must not define a scalar value");
            }
        } else {
            if (value == null) {
                throw new IllegalArgumentException(
                        "Preset scalar property '" + name + "' must define a scalar value");
            }
            if (!items.isEmpty()) {
                throw new IllegalArgumentException(
                        "Preset scalar property '" + name + "' must not define list items");
            }
        }
    }

    public boolean hasScalarValue() {
        return value != null;
    }

    public boolean hasItems() {
        return type == PropertyDef.Type.LIST;
    }
}
