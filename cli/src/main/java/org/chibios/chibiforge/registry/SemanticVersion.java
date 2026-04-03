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

package org.chibios.chibiforge.registry;

/**
 * Simple semantic version for {@code major.minor.patch} comparisons.
 */
final class SemanticVersion implements Comparable<SemanticVersion> {

    private final int major;
    private final int minor;
    private final int patch;
    private final String text;

    private SemanticVersion(int major, int minor, int patch, String text) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.text = text;
    }

    static SemanticVersion parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Version must not be blank");
        }
        String[] parts = text.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid semantic version: " + text);
        }
        return new SemanticVersion(
                Integer.parseInt(parts[0]),
                Integer.parseInt(parts[1]),
                Integer.parseInt(parts[2]),
                text);
    }

    String text() {
        return text;
    }

    @Override
    public int compareTo(SemanticVersion other) {
        int cmp = Integer.compare(major, other.major);
        if (cmp != 0) {
            return cmp;
        }
        cmp = Integer.compare(minor, other.minor);
        if (cmp != 0) {
            return cmp;
        }
        return Integer.compare(patch, other.patch);
    }
}
