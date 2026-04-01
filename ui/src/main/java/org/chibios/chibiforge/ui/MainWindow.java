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
import javafx.stage.Stage;

/**
 * Main application window layout.
 * Three-panel layout: palette (left), center content, inspector (right).
 */
public class MainWindow {

    private final Stage stage;
    private final BorderPane root;

    // Top
    private final MenuBar menuBar;
    private final ToolBar toolBar;
    private final ComboBox<String> targetSelector;
    private final ToggleButton inspectorToggle;

    // Panels
    private final VBox palettePanel;
    private final StackPane centerPanel;
    private final VBox inspectorPanel;

    // Status bar
    private final HBox statusBar;
    private final Label statusLeft;
    private final Label statusRight;

    public MainWindow(Stage stage) {
        this.stage = stage;

        // Menu bar
        menuBar = createMenuBar();

        // Target selector
        targetSelector = new ComboBox<>();
        targetSelector.getItems().add("default");
        targetSelector.getSelectionModel().selectFirst();
        targetSelector.setPrefWidth(150);

        // Toolbar
        inspectorToggle = new ToggleButton("Inspector");
        inspectorToggle.setSelected(true);
        toolBar = createToolBar();

        // Top container
        VBox topContainer = new VBox(menuBar, toolBar);

        // Left panel — component palette placeholder
        palettePanel = new VBox();
        palettePanel.setPrefWidth(250);
        palettePanel.setMinWidth(200);
        palettePanel.getStyleClass().add("palette-panel");
        Label paletteHeader = new Label("Available Components");
        paletteHeader.getStyleClass().add("panel-header");
        paletteHeader.setPadding(new Insets(8));
        palettePanel.getChildren().add(paletteHeader);

        // Center panel placeholder
        centerPanel = new StackPane();
        centerPanel.getStyleClass().add("center-panel");
        Label placeholder = new Label("Open a configuration file to begin");
        placeholder.getStyleClass().add("placeholder-text");
        centerPanel.getChildren().add(placeholder);

        // Right panel — inspector placeholder
        inspectorPanel = new VBox();
        inspectorPanel.setPrefWidth(300);
        inspectorPanel.setMinWidth(200);
        inspectorPanel.getStyleClass().add("inspector-panel");
        Label inspectorHeader = new Label("Inspector");
        inspectorHeader.getStyleClass().add("panel-header");
        inspectorHeader.setPadding(new Insets(8));
        TabPane inspectorTabs = new TabPane(
                new Tab("Outline", new Label("Outline")),
                new Tab("Help", new Label("Help")),
                new Tab("Files", new Label("Files")),
                new Tab("Log", new Label("Log"))
        );
        inspectorTabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        inspectorPanel.getChildren().addAll(inspectorHeader, inspectorTabs);
        VBox.setVgrow(inspectorTabs, Priority.ALWAYS);

        // Inspector toggle
        inspectorToggle.setOnAction(e -> inspectorPanel.setVisible(inspectorToggle.isSelected()));
        inspectorPanel.managedProperty().bind(inspectorPanel.visibleProperty());

        // Status bar
        statusLeft = new Label("No configuration loaded");
        statusRight = new Label("Saved");
        statusBar = createStatusBar();

        // Main layout
        SplitPane splitPane = new SplitPane(palettePanel, centerPanel, inspectorPanel);
        splitPane.setDividerPositions(0.2, 0.75);

        root = new BorderPane();
        root.setTop(topContainer);
        root.setCenter(splitPane);
        root.setBottom(statusBar);
    }

    private MenuBar createMenuBar() {
        // File menu
        Menu fileMenu = new Menu("_File");
        fileMenu.getItems().addAll(
                new MenuItem("New"),
                new MenuItem("Open..."),
                new SeparatorMenuItem(),
                new MenuItem("Save"),
                new MenuItem("Save As..."),
                new SeparatorMenuItem(),
                new MenuItem("Exit")
        );

        // Edit menu
        Menu editMenu = new Menu("_Edit");
        editMenu.getItems().addAll(
                new MenuItem("Undo"),
                new MenuItem("Redo"),
                new SeparatorMenuItem(),
                new MenuItem("Preferences...")
        );

        // Components menu
        Menu componentsMenu = new Menu("_Components");
        componentsMenu.getItems().addAll(
                new MenuItem("Add Component"),
                new MenuItem("Remove Component")
        );

        // Generate menu
        Menu generateMenu = new Menu("_Generate");
        generateMenu.getItems().addAll(
                new MenuItem("Generate"),
                new MenuItem("Clean")
        );

        // Help menu
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
    public ComboBox<String> getTargetSelector() { return targetSelector; }
    public Label getStatusLeft() { return statusLeft; }
    public Label getStatusRight() { return statusRight; }
    public StackPane getCenterPanel() { return centerPanel; }
    public VBox getPalettePanel() { return palettePanel; }
    public VBox getInspectorPanel() { return inspectorPanel; }
}
