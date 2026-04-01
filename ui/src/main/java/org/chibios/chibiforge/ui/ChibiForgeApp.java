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

package org.chibios.chibiforge.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.chibios.chibiforge.ui.model.AppModel;

import java.nio.file.Path;
import java.util.List;

/**
 * ChibiForge JavaFX application entry point.
 *
 * Accepts optional arguments:
 *   --components PATH   filesystem components root
 *   --plugins PATH      plugin JARs root
 *   [configFile]        path to chibiforge.xcfg to open on startup
 */
public class ChibiForgeApp extends Application {

    private static final String APP_TITLE = "ChibiForge";
    private static final double DEFAULT_WIDTH = 1280;
    private static final double DEFAULT_HEIGHT = 800;

    @Override
    public void start(Stage primaryStage) {
        AppModel model = new AppModel();

        // Parse application parameters
        List<String> args = getParameters().getRaw();
        for (int i = 0; i < args.size(); i++) {
            switch (args.get(i)) {
                case "--components" -> {
                    if (i + 1 < args.size()) model.setComponentsRoot(Path.of(args.get(++i)));
                }
                case "--plugins" -> {
                    if (i + 1 < args.size()) model.setPluginsRoot(Path.of(args.get(++i)));
                }
                default -> {
                    // Treat as config file path
                    if (!args.get(i).startsWith("--")) {
                        model.setConfigFile(Path.of(args.get(i)));
                    }
                }
            }
        }

        MainWindow mainWindow = new MainWindow(primaryStage, model);
        Scene scene = new Scene(mainWindow.getRoot(), DEFAULT_WIDTH, DEFAULT_HEIGHT);

        String css = getClass().getResource("/css/light.css").toExternalForm();
        scene.getStylesheets().add(css);

        primaryStage.setTitle(APP_TITLE);
        primaryStage.setScene(scene);
        primaryStage.show();

        // If a config file was provided on the command line, open it
        if (model.getConfigFile() != null) {
            mainWindow.openConfiguration(model.getConfigFile());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
