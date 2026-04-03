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

package org.chibios.chibiforge.ui.settings;

import java.util.ArrayList;
import java.util.List;

public class UiSettings {

    private ThemeMode theme = ThemeMode.LIGHT;
    private List<String> recentFiles = new ArrayList<>();

    public UiSettings() {
    }

    public ThemeMode getTheme() {
        return theme;
    }

    public void setTheme(ThemeMode theme) {
        this.theme = theme != null ? theme : ThemeMode.LIGHT;
    }

    public List<String> getRecentFiles() {
        return recentFiles;
    }

    public void setRecentFiles(List<String> recentFiles) {
        this.recentFiles = recentFiles != null ? new ArrayList<>(recentFiles) : new ArrayList<>();
    }
}
