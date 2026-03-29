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

package org.chibios.chibiforge.datamodel;

/**
 * Normalizes identifiers per the ChibiForge specification (§2.1).
 *
 * Rules:
 * 1. Convert to lowercase.
 * 2. Replace any character that is not a letter, digit, or underscore with '_'.
 * 3. Collapse multiple consecutive underscores into a single '_'.
 */
public final class IdNormalizer {

    private IdNormalizer() {}

    public static String normalize(String id) {
        if (id == null || id.isEmpty()) {
            return id;
        }
        String lower = id.toLowerCase();
        String replaced = lower.replaceAll("[^a-z0-9_]", "_");
        String collapsed = replaced.replaceAll("_+", "_");
        return collapsed;
    }
}
