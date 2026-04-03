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
 * A preset property entry, either scalar-valued or list-nested.
 */
public record PresetProperty(String name, PropertyDef.Type type, String value, List<PresetSection> sections) {

    public PresetProperty {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Preset property name must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("Preset property type must not be null");
        }
        sections = sections != null ? List.copyOf(sections) : List.of();

        boolean hasScalarValue = value != null;
        boolean hasNestedSections = !sections.isEmpty();
        if (hasScalarValue == hasNestedSections) {
            throw new IllegalArgumentException(
                    "Preset property '" + name + "' must define either a scalar value or nested sections");
        }
    }

    public boolean hasScalarValue() {
        return value != null;
    }

    public boolean hasNestedSections() {
        return !sections.isEmpty();
    }
}
