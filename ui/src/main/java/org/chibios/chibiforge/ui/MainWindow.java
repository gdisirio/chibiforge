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
import org.chibios.chibiforge.component.ComponentDefinition;
import org.chibios.chibiforge.config.ChibiForgeConfiguration;
import org.chibios.chibiforge.config.ComponentConfigEntry;
import org.chibios.chibiforge.config.ConfigLoader;
import org.chibios.chibiforge.container.ComponentContainer;
import org.chibios.chibiforge.feature.FeatureChecker;
import org.chibios.chibiforge.registry.ComponentRegistry;
import org.chibios.chibiforge.ui.center.BreadcrumbBar;
import org.chibios.chibiforge.ui.center.ComponentsView;
import org.chibios.chibiforge.ui.center.ConfigurationForm;
import org.chibios.chibiforge.ui.inspector.InspectorPanel;
import org.chibios.chibiforge.ui.io.XcfgWriter;
import org.chibios.chibiforge.ui.targets.ManageTargetsDialog;
import org.chibios.chibiforge.ui.model.AppModel;
import org.chibios.chibiforge.ui.palette.ComponentPalette;
import org.chibios.chibiforge.generator.GenerationAction;
import org.chibios.chibiforge.generator.GenerationContext;
import org.chibios.chibiforge.generator.GenerationReport;
import org.chibios.chibiforge.generator.GeneratorEngine;

import java.io.File;
import java.nio.file.Files;
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
    private final InspectorPanel inspector;
    private final SplitPane splitPane;
    private final BreadcrumbBar breadcrumb;
    private final ComponentsView componentsView;
    private final ConfigurationForm configForm;
    private String lastViewedComponentId;

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

        // Toolbar
        inspectorToggle = new ToggleButton("Inspector");
        inspectorToggle.setSelected(true);
        toolBar = createToolBar();

        VBox topContainer = new VBox(menuBar, toolBar);

        // Left panel — component palette
        palette = new ComponentPalette(model);

        // Breadcrumb
        breadcrumb = new BreadcrumbBar();
        breadcrumb.setOnNavigate(index -> {
            if (index == 0) {
                showComponentsView();
            } else if (index == 1 && lastViewedComponentId != null) {
                showConfigurationForm(lastViewedComponentId);
            }
        });

        // Configuration form
        configForm = new ConfigurationForm(model);
        // Bind target selector to model (after configForm is created)
        targetSelector.setOnAction(e -> {
            String selected = targetSelector.getSelectionModel().getSelectedItem();
            if (selected != null) {
                model.setActiveTarget(selected);
                configForm.refreshForTarget(selected);
            }
        });

        configForm.setOnListDrillDown(dd -> {
            // Navigate into list item
            breadcrumb.setPath(
                    breadcrumb.getSegments().get(0),
                    breadcrumb.getSegments().size() > 1 ? breadcrumb.getSegments().get(1) : "?",
                    dd.listProperty.getName() + " [" + dd.itemIndex + "]");
            configForm.loadListItem(dd.listProperty, dd.itemElement);
        });

        // Components view
        componentsView = new ComponentsView(model);
        componentsView.setOnComponentDoubleClick(compId -> showConfigurationForm(compId));
        componentsView.setOnAddSelected(() -> addSelectedComponent());

        // Center panel
        centerPanel = new StackPane();
        centerPanel.getStyleClass().add("center-panel");
        Label placeholder = new Label("Open a configuration file to begin");
        placeholder.getStyleClass().add("placeholder-text");

        VBox centerContainer = new VBox(breadcrumb, centerPanel);
        VBox.setVgrow(centerPanel, Priority.ALWAYS);

        // Right panel — inspector
        inspector = new InspectorPanel(model);

        // Status bar
        statusLeft = new Label("No configuration loaded");
        statusRight = new Label("Saved");
        HBox statusBar = createStatusBar();

        // Main layout
        splitPane = new SplitPane(palette.getRoot(), centerContainer, inspector.getRoot());
        splitPane.setDividerPositions(0.15, 0.78);

        // Inspector toggle
        inspectorToggle.setOnAction(e -> {
            if (inspectorToggle.isSelected()) {
                splitPane.getItems().add(inspector.getRoot());
                splitPane.setDividerPosition(splitPane.getDividers().size() - 1, 0.75);
            } else {
                splitPane.getItems().remove(inspector.getRoot());
            }
        });

        root = new BorderPane();
        root.setTop(topContainer);
        root.setCenter(splitPane);
        root.setBottom(statusBar);

        // Escape key navigates up
        root.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE) {
                breadcrumb.navigateUp();
                e.consume();
            }
        });

        // Close confirmation
        stage.setOnCloseRequest(e -> {
            if (model.isModified()) {
                Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                        "Save changes before closing?",
                        new ButtonType("Save", ButtonBar.ButtonData.YES),
                        new ButtonType("Don't Save", ButtonBar.ButtonData.NO),
                        ButtonType.CANCEL);
                confirm.setHeaderText(null);
                var result = confirm.showAndWait();
                if (result.isEmpty() || result.get() == ButtonType.CANCEL) {
                    e.consume();
                    return;
                }
                if (result.get().getButtonData() == ButtonBar.ButtonData.YES) {
                    saveConfiguration();
                }
            }
        });

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

            // Show components view
            showComponentsView();

        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Failed to open configuration:\n" + e.getMessage(),
                    ButtonType.OK);
            alert.setTitle("Error");
            alert.showAndWait();
        }
    }

    /**
     * Show the top-level components card view.
     */
    private void showComponentsView() {
        breadcrumb.setPath("Components");
        centerPanel.getChildren().clear();
        centerPanel.getChildren().add(componentsView.getRoot());
        componentsView.refresh();
        inspector.showComponentsOutline();
        inspector.showConfigurationHelp();
        inspector.refreshFiles();
        updateStatusBar();
    }

    /**
     * Show the configuration form for a specific component.
     */
    private void showConfigurationForm(String componentId) {
        lastViewedComponentId = componentId;
        try {
            ComponentContainer container = model.getRegistry().lookup(componentId);
            ComponentDefinition def = container.loadDefinition();

            // Find the config entry for this component
            ComponentConfigEntry configEntry = null;
            for (ComponentConfigEntry entry : model.getConfiguration().getComponents()) {
                if (entry.getComponentId().equals(componentId)) {
                    configEntry = entry;
                    break;
                }
            }
            if (configEntry == null) return;

            breadcrumb.setPath("Components", def.getName());
            configForm.loadComponent(def, configEntry, container);

            centerPanel.getChildren().clear();
            centerPanel.getChildren().add(configForm.getRoot());

            inspector.showComponentOutline(def);
            inspector.showComponentHelp(def);
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Failed to load component form:\n" + e.getMessage(), ButtonType.OK);
            alert.showAndWait();
        }
    }

    /**
     * Add the component currently selected in the palette to the configuration.
     */
    private void addSelectedComponent() {
        String compId = palette.getSelectedComponentId();
        if (compId == null) return;

        // Check if already configured
        if (model.getConfiguredComponentIds().contains(compId)) {
            Alert alert = new Alert(Alert.AlertType.INFORMATION,
                    "Component is already in the configuration.", ButtonType.OK);
            alert.setHeaderText(null);
            alert.showAndWait();
            return;
        }

        // Add to the xcfg DOM
        var config = model.getConfiguration();
        if (config == null) return;

        try {
            // Find the <components> element and add a new <component id="..."/>
            var firstEntry = config.getComponents().isEmpty() ? null : config.getComponents().get(0);
            if (firstEntry != null) {
                var componentsElement = firstEntry.getConfigElement().getParentNode();
                var doc = firstEntry.getConfigElement().getOwnerDocument();
                var newComp = doc.createElementNS(
                        firstEntry.getConfigElement().getNamespaceURI(), "component");
                newComp.setAttribute("id", compId);
                componentsElement.appendChild(newComp);
            }

            // Reload the configuration to pick up the new component
            // TODO: proper incremental model update
            Path configFile = model.getConfigFile();
            if (configFile != null) {
                // Re-parse to get updated component list
                var loader = new org.chibios.chibiforge.config.ConfigLoader();
                // For now, mark modified and refresh views
                model.setModified(true);
            }

            componentsView.refresh();
            palette.refresh();
            updateStatusBar();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Failed to add component: " + e.getMessage(), ButtonType.OK);
            alert.showAndWait();
        }
    }

    private String getComponentName(String componentId) {
        try {
            if (model.getRegistry() != null) {
                return model.getRegistry().lookup(componentId).loadDefinition().getName();
            }
        } catch (Exception ignored) {}
        return componentId;
    }

    private void updateStatusBar() {
        var config = model.getConfiguration();
        var registry = model.getRegistry();
        if (config != null && registry != null) {
            int compCount = config.getComponents().size();
            statusLeft.setText(compCount + " component(s) configured, " +
                    registry.size() + " available, target: " + model.getActiveTarget());
        }
        if (!model.getWarnings().isEmpty()) {
            statusRight.setText(model.getWarnings().size() + " warning(s)");
        }
    }

    private void saveConfiguration() {
        if (model.getConfigFile() == null || model.getConfiguration() == null) return;

        try {
            // Get the document root from any component's config element
            var components = model.getConfiguration().getComponents();
            if (components.isEmpty()) return;
            var element = components.get(0).getConfigElement();
            var docElement = element.getOwnerDocument().getDocumentElement();

            // Collect text property names from all component schemas
            java.util.Set<String> textPropertyNames = collectTextPropertyNames();

            XcfgWriter writer = new XcfgWriter();
            writer.save(docElement, model.getConfigFile(), textPropertyNames);
            model.setModified(false);
            inspector.appendLog("Saved: " + model.getConfigFile());
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Failed to save:\n" + e.getMessage(), ButtonType.OK);
            alert.showAndWait();
        }
    }

    private java.util.Set<String> collectTextPropertyNames() {
        java.util.Set<String> names = new java.util.HashSet<>();
        if (model.getRegistry() == null || model.getConfiguration() == null) return names;

        for (var entry : model.getConfiguration().getComponents()) {
            try {
                var container = model.getRegistry().lookup(entry.getComponentId());
                var def = container.loadDefinition();
                collectTextProperties(def.getSections(), names);
            } catch (Exception ignored) {}
        }
        return names;
    }

    private void collectTextProperties(java.util.List<org.chibios.chibiforge.component.SectionDef> sections,
                                       java.util.Set<String> names) {
        for (var section : sections) {
            for (Object child : section.getChildren()) {
                if (child instanceof org.chibios.chibiforge.component.PropertyDef prop) {
                    if (prop.getType() == org.chibios.chibiforge.component.PropertyDef.Type.TEXT) {
                        names.add(prop.getName());
                    }
                    collectTextProperties(prop.getNestedSections(), names);
                } else if (child instanceof org.chibios.chibiforge.component.LayoutDef layout) {
                    for (Object lc : layout.getChildren()) {
                        if (lc instanceof org.chibios.chibiforge.component.PropertyDef prop) {
                            if (prop.getType() == org.chibios.chibiforge.component.PropertyDef.Type.TEXT) {
                                names.add(prop.getName());
                            }
                        }
                    }
                }
            }
        }
    }

    private void runGenerate() {
        if (model.getConfigFile() == null) return;

        // Prompt to save if modified
        if (model.isModified()) {
            Alert savePrompt = new Alert(Alert.AlertType.CONFIRMATION,
                    "Save changes before generating?",
                    new ButtonType("Save", ButtonBar.ButtonData.YES),
                    new ButtonType("Don't Save", ButtonBar.ButtonData.NO),
                    ButtonType.CANCEL);
            savePrompt.setHeaderText(null);
            var result = savePrompt.showAndWait();
            if (result.isEmpty() || result.get() == ButtonType.CANCEL) return;
            if (result.get().getButtonData() == ButtonBar.ButtonData.YES) {
                saveConfiguration();
            }
        }

        inspector.showLogTab();
        inspector.appendLog("--- Generate started: target=" + model.getActiveTarget() + " ---");

        try {
            GenerationContext ctx = new GenerationContext(
                    model.getConfigFile(), model.getConfigRoot(),
                    model.getActiveTarget(), false, true);
            GeneratorEngine engine = new GeneratorEngine();
            GenerationReport report = engine.generate(ctx,
                    model.getComponentsRoot(), model.getPluginsRoot());

            for (var action : report.getActions()) {
                inspector.appendLog("  " + action);
            }
            for (String warning : report.getWarnings()) {
                inspector.appendLog("  WARNING: " + warning);
            }
            inspector.appendLog("Generation complete: " +
                    report.countByType(GenerationAction.Type.COPY) + " copied, " +
                    report.countByType(GenerationAction.Type.SKIP) + " skipped, " +
                    report.countByType(GenerationAction.Type.TEMPLATE) + " templates.");

            inspector.refreshFiles();
            statusRight.setText("Generated");
        } catch (Exception e) {
            inspector.appendLog("ERROR: " + e.getMessage());
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Generation failed:\n" + e.getMessage(), ButtonType.OK);
            alert.showAndWait();
        }
    }

    private void runClean() {
        if (model.getConfigRoot() == null) return;

        java.nio.file.Path generatedDir = model.getConfigRoot().resolve("generated");
        if (!Files.isDirectory(generatedDir)) {
            inspector.appendLog("Nothing to clean: generated/ does not exist.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete all files in generated/? This cannot be undone.",
                ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        confirm.showAndWait().ifPresent(btn -> {
            if (btn == ButtonType.OK) {
                try {
                    deleteRecursive(generatedDir);
                    inspector.showLogTab();
                    inspector.appendLog("Cleaned: " + generatedDir);
                    inspector.refreshFiles();
                    statusRight.setText("Cleaned");
                } catch (Exception e) {
                    inspector.appendLog("ERROR: " + e.getMessage());
                }
            }
        });
    }

    private void deleteRecursive(java.nio.file.Path dir) throws Exception {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> {
                    try { Files.delete(p); } catch (Exception ignored) {}
                });
        }
    }

    private void showManageTargetsDialog() {
        ManageTargetsDialog dialog = new ManageTargetsDialog(model.getTargets());
        dialog.showAndWait().ifPresent(newTargets -> {
            String previousTarget = model.getActiveTarget();
            model.getTargets().setAll(newTargets);
            targetSelector.getItems().setAll(newTargets);
            if (newTargets.contains(previousTarget)) {
                targetSelector.getSelectionModel().select(previousTarget);
            } else {
                targetSelector.getSelectionModel().select("default");
                model.setActiveTarget("default");
            }
            model.setModified(true);
        });
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
        MenuItem saveItem = new MenuItem("Save");
        saveItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("Ctrl+S"));
        saveItem.setOnAction(e -> saveConfiguration());

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> stage.close());

        fileMenu.getItems().addAll(
                new MenuItem("New"),
                openItem,
                new SeparatorMenuItem(),
                saveItem,
                new MenuItem("Save As..."),
                new SeparatorMenuItem(),
                exitItem
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

        MenuItem generateItem = new MenuItem("Generate");
        generateItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("Ctrl+G"));
        generateItem.setOnAction(e -> runGenerate());
        MenuItem cleanItem = new MenuItem("Clean");
        cleanItem.setOnAction(e -> runClean());

        Menu generateMenu = new Menu("_Generate");
        generateMenu.getItems().addAll(generateItem, cleanItem);

        Menu helpMenu = new Menu("_Help");
        helpMenu.getItems().addAll(
                new MenuItem("About"),
                new MenuItem("Documentation")
        );

        return new MenuBar(fileMenu, editMenu, componentsMenu, generateMenu, helpMenu);
    }

    private ToolBar createToolBar() {
        Button saveBtn = new Button("Save");
        saveBtn.setOnAction(e -> saveConfiguration());
        Separator sep1 = new Separator();
        Button generateBtn = new Button("Generate");
        generateBtn.getStyleClass().add("accent-button");
        generateBtn.setOnAction(e -> runGenerate());
        Button cleanBtn = new Button("Clean");
        cleanBtn.setOnAction(e -> runClean());
        Label targetLabel = new Label("Target:");

        Button manageTargetsBtn = new Button("\u2699");
        manageTargetsBtn.setTooltip(new Tooltip("Manage Targets..."));
        manageTargetsBtn.setOnAction(e -> showManageTargetsDialog());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return new ToolBar(
                saveBtn, sep1,
                generateBtn, cleanBtn,
                new Separator(),
                targetLabel, targetSelector, manageTargetsBtn,
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
