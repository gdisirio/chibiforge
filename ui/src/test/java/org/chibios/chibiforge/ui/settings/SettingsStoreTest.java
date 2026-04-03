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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SettingsStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void roundTripsSettingsJson() throws Exception {
        Path settingsFile = tempDir.resolve("settings.json");
        SettingsStore store = new SettingsStore(settingsFile);

        UiSettings settings = new UiSettings();
        settings.setTheme(ThemeMode.DARK);
        settings.setRecentFiles(List.of("/tmp/one.xcfg", "/tmp/two.xcfg"));

        store.save(settings);
        UiSettings reloaded = store.load();

        assertThat(Files.isRegularFile(settingsFile)).isTrue();
        assertThat(reloaded.getTheme()).isEqualTo(ThemeMode.DARK);
        assertThat(reloaded.getRecentFiles()).containsExactly("/tmp/one.xcfg", "/tmp/two.xcfg");
    }

    @Test
    void returnsDefaultsWhenSettingsFileIsMissing() {
        SettingsStore store = new SettingsStore(tempDir.resolve("missing.json"));

        UiSettings settings = store.load();

        assertThat(settings.getTheme()).isEqualTo(ThemeMode.LIGHT);
        assertThat(settings.getRecentFiles()).isEmpty();
    }
}
