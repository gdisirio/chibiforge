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

import org.w3c.dom.Element;

/**
 * A single component's configuration values from chibiforge.xcfg.
 * Stores the component ID and the raw DOM element containing section/property values.
 */
public class ComponentConfigEntry {
    private final String componentId;
    private final String componentVersion;
    private final Element configElement;

    public ComponentConfigEntry(String componentId, String componentVersion, Element configElement) {
        this.componentId = componentId;
        this.componentVersion = componentVersion;
        this.configElement = configElement;
    }

    public String getComponentId() { return componentId; }
    public String getComponentVersion() { return componentVersion; }

    /**
     * Returns the raw {@code <component>} element from the xcfg file.
     * Children are section elements containing property values.
     */
    public Element getConfigElement() { return configElement; }

    @Override
    public String toString() {
        return "ComponentConfigEntry{componentId='" + componentId +
                "', componentVersion='" + componentVersion + "'}";
    }
}
