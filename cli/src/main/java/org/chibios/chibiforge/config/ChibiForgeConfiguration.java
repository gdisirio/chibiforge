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

import java.util.List;

/**
 * Parsed representation of a chibiforge.xcfg configuration file.
 */
public class ChibiForgeConfiguration {
    private final String toolVersion;
    private final String schemaVersion;
    private final List<String> targets;
    private final List<ComponentConfigEntry> components;

    public ChibiForgeConfiguration(String toolVersion, String schemaVersion,
                                   List<String> targets, List<ComponentConfigEntry> components) {
        this.toolVersion = toolVersion;
        this.schemaVersion = schemaVersion;
        this.targets = targets != null ? List.copyOf(targets) : List.of("default");
        this.components = components != null ? List.copyOf(components) : List.of();
    }

    public String getToolVersion() { return toolVersion; }
    public String getSchemaVersion() { return schemaVersion; }
    public List<String> getTargets() { return targets; }
    public List<ComponentConfigEntry> getComponents() { return components; }

    @Override
    public String toString() {
        return "ChibiForgeConfiguration{toolVersion='" + toolVersion +
                "', targets=" + targets + ", components=" + components.size() + "}";
    }
}
