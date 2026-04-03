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
 * Parsed representation of a preset XML document.
 */
public record PresetDefinition(String name, String componentId, String version, List<PresetSection> sections) {

    public PresetDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Preset name must not be blank");
        }
        if (componentId == null || componentId.isBlank()) {
            throw new IllegalArgumentException("Preset component ID must not be blank");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Preset version must not be blank");
        }
        sections = sections != null ? List.copyOf(sections) : List.of();
    }
}
