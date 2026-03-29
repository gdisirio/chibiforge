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

package org.chibios.chibiforge.config;

/**
 * Resolves and validates the active target for generation.
 * Phase 1: only "default" target is supported.
 */
public class TargetResolver {

    public String resolve(ChibiForgeConfiguration config, String requestedTarget) {
        if (requestedTarget == null || requestedTarget.isBlank()) {
            return "default";
        }
        if (!config.getTargets().contains(requestedTarget)) {
            throw new IllegalArgumentException(
                    "Target '" + requestedTarget + "' not defined in configuration. " +
                    "Available targets: " + config.getTargets());
        }
        return requestedTarget;
    }
}
