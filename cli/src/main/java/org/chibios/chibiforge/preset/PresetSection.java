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

import java.util.List;

/**
 * A preset section identified by its schema-visible name.
 */
public record PresetSection(String name, List<PresetProperty> properties) {

    public PresetSection {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Preset section name must not be blank");
        }
        properties = properties != null ? List.copyOf(properties) : List.of();
    }
}
