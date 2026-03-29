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

package org.chibios.chibiforge.generator;

/**
 * Describes a single action taken (or planned) during generation.
 */
public class GenerationAction {

    public enum Type {
        COPY,       // Static file copied
        SKIP,       // Static file skipped (write-once, already exists)
        TEMPLATE    // Template processed
    }

    private final Type type;
    private final String source;
    private final String destination;
    private final String reason;

    public GenerationAction(Type type, String source, String destination, String reason) {
        this.type = type;
        this.source = source;
        this.destination = destination;
        this.reason = reason;
    }

    public Type getType() { return type; }
    public String getSource() { return source; }
    public String getDestination() { return destination; }
    public String getReason() { return reason; }

    @Override
    public String toString() {
        return type + ": " + source + " -> " + destination +
                (reason != null ? " (" + reason + ")" : "");
    }
}
