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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class SettingsStore {

    private final Path settingsPath;
    private final ObjectMapper objectMapper;

    public SettingsStore() {
        this(resolveDefaultSettingsPath());
    }

    SettingsStore(Path settingsPath) {
        this.settingsPath = settingsPath;
        this.objectMapper = new ObjectMapper()
                .configure(SerializationFeature.INDENT_OUTPUT, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public Path getSettingsPath() {
        return settingsPath;
    }

    public UiSettings load() {
        if (!Files.isRegularFile(settingsPath)) {
            return new UiSettings();
        }
        try {
            UiSettings loaded = objectMapper.readValue(settingsPath.toFile(), UiSettings.class);
            return loaded != null ? loaded : new UiSettings();
        } catch (IOException ignored) {
            return new UiSettings();
        }
    }

    public void save(UiSettings settings) throws IOException {
        Files.createDirectories(settingsPath.getParent());
        objectMapper.writeValue(settingsPath.toFile(), settings);
    }

    private static Path resolveDefaultSettingsPath() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String userHome = System.getProperty("user.home");
        if (userHome == null || userHome.isBlank()) {
            userHome = ".";
        }

        if (osName.contains("win")) {
            String appData = System.getenv("APPDATA");
            Path base = (appData != null && !appData.isBlank())
                    ? Path.of(appData)
                    : Path.of(userHome, "AppData", "Roaming");
            return base.resolve("ChibiForge").resolve("settings.json");
        }

        if (osName.contains("mac")) {
            return Path.of(userHome, "Library", "Application Support", "ChibiForge", "settings.json");
        }

        String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
        Path base = (xdgConfigHome != null && !xdgConfigHome.isBlank())
                ? Path.of(xdgConfigHome)
                : Path.of(userHome, ".config");
        return base.resolve("chibiforge").resolve("settings.json");
    }
}
