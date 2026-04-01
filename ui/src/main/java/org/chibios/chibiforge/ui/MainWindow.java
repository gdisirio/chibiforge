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

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.chibios.chibiforge.config.ChibiForgeConfiguration;
import org.chibios.chibiforge.config.ConfigLoader;
import org.chibios.chibiforge.feature.FeatureChecker;
import org.chibios.chibiforge.registry.ComponentRegistry;
import org.chibios.chibiforge.ui.model.AppModel;
import org.chibios.chibiforge.ui.palette.ComponentPalette;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/**
 * Main application window layout.
 */
public class MainWindow {

    private final Stage stage;
    private final AppModel model;
    private final BorderPane root;

    // Top
    private final MenuBar menuBar;
    private final ToolBar toolBar;
    private final ComboBox<String> targetSelector;
    private final ToggleButton inspectorToggle;

    // Panels
    private final ComponentPalette palette;
    private final StackPane centerPanel;
    private final VBox inspectorPanel;
    private final SplitPane splitPane;

    // Status bar
    private final Label statusLeft;
    private final Label statusRight;

    public MainWindow(Stage stage, AppModel model) {
        this.stage = stage;
        this.model = model;

        // Menu bar
        menuBar = createMenuBar();

        // Target selector
        targetSelector = new ComboBox<>();
        targetSelector.getItems().add("default");
        targetSelector.getSelectionModel().selectFirst();
        targetSelector.setPrefWidth(150);

        // Bind target selector to model
        targetSelector.setOnAction(e -> {
            String selected = targetSelector.getSelectionModel().getSelectedItem();
            if (selected != null) model.setActiveTarget(selected);
        });

        // Toolbar
        inspectorToggle = new ToggleButton("Inspector");
        inspectorToggle.setSelected(true);
        toolBar = createToolBar();

        VBox topContainer = new VBox(menuBar, toolBar);

        // Left panel — component palette
        palette = new ComponentPalette(model);

        // Center panel
        centerPanel = new StackPane();
        centerPanel.getStyleClass().add("center-panel");
        Label placeholder = new Label("Open a configuration file to begin");
        placeholder.getStyleClass().add("placeholder-text");
        centerPanel.getChildren().add(placeholder);

        // Right panel — inspector
        inspectorPanel = createInspectorPanel();

        // Status bar
        statusLeft = new Label("No configuration loaded");
        statusRight = new Label("Saved");
        HBox statusBar = createStatusBar();

        // Main layout
        splitPane = new SplitPane(palette.getRoot(), centerPanel, inspectorPanel);
        splitPane.setDividerPositions(0.2, 0.75);

        // Inspector toggle
        inspectorToggle.setOnAction(e -> {
            if (inspectorToggle.isSelected()) {
                splitPane.getItems().add(inspectorPanel);
                splitPane.setDividerPosition(splitPane.getDividers().size() - 1, 0.75);
            } else {
                splitPane.getItems().remove(inspectorPanel);
            }
        });

        root = new BorderPane();
        root.setTop(topContainer);
        root.setCenter(splitPane);
        root.setBottom(statusBar);

        // Bind title bar to config file path
        model.configFileProperty().addListener((obs, old, path) -> {
            stage.setTitle(path != null ? "ChibiForge - " + path : "ChibiForge");
        });

        // Bind status bar to model
        model.modifiedProperty().addListener((obs, old, mod) -> {
            statusRight.setText(mod ? "Modified" : "Saved");
        });
    }

    /**
     * Open a configuration file and load it into the model.
     */
    public void openConfiguration(Path configFile) {
        try {
            ConfigLoader loader = new ConfigLoader();
            ChibiForgeConfiguration config = loader.load(configFile);

            Path configRoot = configFile.getParent();
            if (configRoot == null) configRoot = Path.of(".");

            model.setConfigFile(configFile.toAbsolutePath());
            model.setConfigRoot(configRoot.toAbsolutePath());
            model.setConfiguration(config);
            model.setModified(false);

            // Update targets
            model.getTargets().setAll(config.getTargets());
            targetSelector.getItems().setAll(config.getTargets());
            targetSelector.getSelectionModel().select(model.getActiveTarget());

            // Build component registry
            ComponentRegistry registry = ComponentRegistry.build(
                    model.getComponentsRoot(), model.getPluginsRoot());
            model.setRegistry(registry);

            // Check feature dependencies
            model.getWarnings().clear();
            FeatureChecker checker = new FeatureChecker();
            var definitions = new java.util.ArrayList<org.chibios.chibiforge.component.ComponentDefinition>();
            for (var entry : config.getComponents()) {
                try {
                    var container = registry.lookup(entry.getComponentId());
                    definitions.add(container.loadDefinition());
                } catch (Exception ignored) {
                    // Component not found — will show as warning
                }
            }
            List<String> warnings = checker.check(definitions);
            model.getWarnings().setAll(warnings);

            // Update palette
            palette.refresh();

            // Update status
            int compCount = config.getComponents().size();
            int regCount = registry.size();
            statusLeft.setText(compCount + " component(s) configured, " +
                    regCount + " available, target: " + model.getActiveTarget());

            if (!warnings.isEmpty()) {
                statusRight.setText(warnings.size() + " warning(s)");
            }

            // Update center panel
            centerPanel.getChildren().clear();
            Label loaded = new Label("Configuration loaded: " + compCount + " component(s)");
            loaded.getStyleClass().add("placeholder-text");
            centerPanel.getChildren().add(loaded);

        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Failed to open configuration:\n" + e.getMessage(),
                    ButtonType.OK);
            alert.setTitle("Error");
            alert.showAndWait();
        }
    }

    private VBox createInspectorPanel() {
        VBox panel = new VBox();
        panel.setPrefWidth(300);
        panel.setMinWidth(200);
        panel.getStyleClass().add("inspector-panel");
        Label header = new Label("Inspector");
        header.getStyleClass().add("panel-header");
        header.setPadding(new Insets(8));
        TabPane tabs = new TabPane(
                new Tab("Outline", new Label("Outline")),
                new Tab("Help", new Label("Help")),
                new Tab("Files", new Label("Files")),
                new Tab("Log", new Label("Log"))
        );
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        panel.getChildren().addAll(header, tabs);
        VBox.setVgrow(tabs, Priority.ALWAYS);
        return panel;
    }

    private MenuBar createMenuBar() {
        Menu fileMenu = new Menu("_File");
        MenuItem openItem = new MenuItem("Open...");
        openItem.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Open ChibiForge Configuration");
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("ChibiForge Configuration", "*.xcfg"));
            File file = chooser.showOpenDialog(stage);
            if (file != null) {
                openConfiguration(file.toPath());
            }
        });
        fileMenu.getItems().addAll(
                new MenuItem("New"),
                openItem,
                new SeparatorMenuItem(),
                new MenuItem("Save"),
                new MenuItem("Save As..."),
                new SeparatorMenuItem(),
                new MenuItem("Exit")
        );

        Menu editMenu = new Menu("_Edit");
        editMenu.getItems().addAll(
                new MenuItem("Undo"),
                new MenuItem("Redo"),
                new SeparatorMenuItem(),
                new MenuItem("Preferences...")
        );

        Menu componentsMenu = new Menu("_Components");
        componentsMenu.getItems().addAll(
                new MenuItem("Add Component"),
                new MenuItem("Remove Component")
        );

        Menu generateMenu = new Menu("_Generate");
        generateMenu.getItems().addAll(
                new MenuItem("Generate"),
                new MenuItem("Clean")
        );

        Menu helpMenu = new Menu("_Help");
        helpMenu.getItems().addAll(
                new MenuItem("About"),
                new MenuItem("Documentation")
        );

        return new MenuBar(fileMenu, editMenu, componentsMenu, generateMenu, helpMenu);
    }

    private ToolBar createToolBar() {
        Button saveBtn = new Button("Save");
        Separator sep1 = new Separator();
        Button generateBtn = new Button("Generate");
        generateBtn.getStyleClass().add("accent-button");
        Button cleanBtn = new Button("Clean");
        Label targetLabel = new Label("Target:");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return new ToolBar(
                saveBtn, sep1,
                generateBtn, cleanBtn,
                new Separator(),
                targetLabel, targetSelector,
                spacer,
                inspectorToggle
        );
    }

    private HBox createStatusBar() {
        HBox bar = new HBox();
        bar.getStyleClass().add("status-bar");
        bar.setPadding(new Insets(4, 8, 4, 8));
        bar.setAlignment(Pos.CENTER_LEFT);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bar.getChildren().addAll(statusLeft, spacer, statusRight);
        return bar;
    }

    public BorderPane getRoot() { return root; }
    public Stage getStage() { return stage; }
    public AppModel getModel() { return model; }
    public StackPane getCenterPanel() { return centerPanel; }
    public ComponentPalette getPalette() { return palette; }
}
