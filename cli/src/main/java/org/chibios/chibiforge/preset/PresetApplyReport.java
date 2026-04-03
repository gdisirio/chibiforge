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
import java.util.Objects;

/**
 * Result of applying a preset to a component configuration.
 */
public record PresetApplyReport(int updatedCount, int ignoredCount, int unchangedCount, List<String> warnings) {

    public PresetApplyReport {
        if (updatedCount < 0 || ignoredCount < 0 || unchangedCount < 0) {
            throw new IllegalArgumentException("Preset apply counts must be non-negative");
        }
        Objects.requireNonNull(warnings, "warnings");
        warnings = List.copyOf(warnings);
    }
}
