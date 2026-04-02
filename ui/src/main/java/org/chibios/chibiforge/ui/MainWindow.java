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
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.chibios.chibiforge.component.ComponentDefinition;
import org.chibios.chibiforge.component.LayoutDef;
import org.chibios.chibiforge.component.PropertyDef;
import org.chibios.chibiforge.component.SectionDef;
import org.chibios.chibiforge.config.ChibiForgeConfiguration;
import org.chibios.chibiforge.config.ComponentConfigEntry;
import org.chibios.chibiforge.config.ConfigLoader;
import org.chibios.chibiforge.container.ComponentContainer;
import org.chibios.chibiforge.datamodel.IdNormalizer;
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
import org.chibios.chibiforge.ui.sources.ComponentSourceResolver;
import org.chibios.chibiforge.ui.sources.ResolvedComponentSources;
import org.chibios.chibiforge.generator.GenerationAction;
import org.chibios.chibiforge.generator.GenerationContext;
import org.chibios.chibiforge.generator.GenerationReport;
import org.chibios.chibiforge.generator.GeneratorEngine;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.prefs.Preferences;

import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Main application window layout.
 */
public class MainWindow {

    private static final String CONFIG_NS = "http://chibiforge/schema/config";
    private static final String DEFAULT_TOOL_VERSION = "1.0.0";
    private static final String DEFAULT_SCHEMA_VERSION = "1.0";
    private static final int MAX_RECENT_FILES = 10;
    private static final String RECENT_FILES_KEY = "recentFiles";

    private final Stage stage;
    private final AppModel model;
    private final BorderPane root;
    private final Preferences preferences;

    // Top
    private final MenuBar menuBar;
    private final Menu recentFilesMenu;
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
    private final List<Path> recentFiles = new ArrayList<>();

    public MainWindow(Stage stage, AppModel model) {
        this.stage = stage;
        this.model = model;
        this.preferences = Preferences.userNodeForPackage(MainWindow.class);

        // Menu bar
        recentFilesMenu = new Menu("Recent Files");
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
        componentsView.setOnRemoveSelected(this::removeConfiguredComponent);

        // Center panel
        centerPanel = new StackPane();
        centerPanel.getStyleClass().add("center-panel");
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
            if (!confirmSafeToDiscardChanges("closing")) {
                e.consume();
            }
        });

        // Bind title bar to config file path
        model.configFileProperty().addListener((obs, old, path) -> updateWindowTitle());
        model.configurationProperty().addListener((obs, old, config) -> updateWindowTitle());

        // Bind status bar to model
        model.modifiedProperty().addListener((obs, old, mod) -> {
            updateStatusBar();
            updateWindowTitle();
        });

        loadRecentFiles();
        rebuildRecentFilesMenu();
        showWelcomeScreen();
        updateWindowTitle();
        updateStatusBar();
    }

    /**
     * Open a configuration file and load it into the model.
     */
    public void openConfiguration(Path configFile) {
        try {
            ConfigLoader loader = new ConfigLoader();
            ConfigLoader.LoadedConfiguration loaded = loader.loadWithDocument(configFile);
            applyConfiguration(loaded.configuration(), loaded.rootElement(), configFile.toAbsolutePath());
            rememberRecentFile(configFile);
            showComponentsView();

        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Failed to open configuration:\n" + e.getMessage(),
                    ButtonType.OK);
            alert.setTitle("Error");
            alert.showAndWait();
        }
    }

    private void newConfiguration() {
        try {
            Document doc = createEmptyConfigurationDocument();
            ConfigLoader loader = new ConfigLoader();
            ChibiForgeConfiguration config = loader.load(doc);
            applyConfiguration(config, doc.getDocumentElement(), null);
            model.setModified(false);
            showComponentsView();
            inspector.appendLog("Created new configuration");
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Failed to create configuration:\n" + e.getMessage(),
                    ButtonType.OK);
            alert.setTitle("Error");
            alert.showAndWait();
        }
    }

    private void requestNewConfiguration() {
        if (confirmSafeToDiscardChanges("creating a new configuration")) {
            newConfiguration();
        }
    }

    private void applyConfiguration(ChibiForgeConfiguration config, Element rootElement, Path configFile) throws Exception {
        Path absoluteConfigFile = configFile != null ? configFile.toAbsolutePath() : null;
        Path configRoot = absoluteConfigFile != null
                ? absoluteConfigFile.getParent()
                : null;

        model.setConfigFile(absoluteConfigFile);
        model.setConfigRoot(configRoot);
        model.setConfigurationRootElement(rootElement);
        model.setConfiguration(config);

        updateTargets(config);
        refreshRegistryAndWarnings(absoluteConfigFile, config);
        palette.refresh();
        inspector.refreshFiles();
        inspector.showConfigurationHelp();
        model.setModified(false);
        updateStatusBar();
    }

    private void updateTargets(ChibiForgeConfiguration config) {
        model.getTargets().setAll(config.getTargets());
        targetSelector.getItems().setAll(config.getTargets());
        String activeTarget = model.getActiveTarget();
        if (activeTarget == null || !config.getTargets().contains(activeTarget)) {
            activeTarget = "default";
            model.setActiveTarget(activeTarget);
        }
        targetSelector.getSelectionModel().select(activeTarget);
    }

    private void refreshRegistryAndWarnings(Path configFile, ChibiForgeConfiguration config) throws Exception {
        ResolvedComponentSources resolvedSources = configFile != null
                ? resolveComponentSources(configFile)
                : resolvePreferredComponentSources();

        model.getResolvedComponentRoots().setAll(resolvedSources.roots());
        ComponentRegistry registry = ComponentRegistry.build(resolvedSources.roots());
        model.setRegistry(registry);

        List<String> warnings = new ArrayList<>(resolvedSources.warnings());
        model.getUnresolvedComponents().clear();

        FeatureChecker checker = new FeatureChecker();
        List<ComponentDefinition> definitions = new ArrayList<>();
        for (ComponentConfigEntry entry : config.getComponents()) {
            try {
                ComponentContainer container = registry.lookup(entry.getComponentId());
                definitions.add(container.loadDefinition());
            } catch (Exception ignored) {
                model.getUnresolvedComponents().add(entry.getComponentId());
            }
        }

        for (String unresolved : model.getUnresolvedComponents()) {
            warnings.add("Component '" + unresolved + "' could not be resolved from the discovered component roots.");
        }
        warnings.addAll(checker.check(definitions));
        model.getWarnings().setAll(warnings);
    }

    private ResolvedComponentSources resolvePreferredComponentSources() {
        List<Path> roots = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (model.getComponentsRoot() != null) {
            if (Files.exists(model.getComponentsRoot())) {
                roots.add(model.getComponentsRoot().toAbsolutePath().normalize());
            } else {
                warnings.add("Components root is invalid: " + model.getComponentsRoot());
            }
        }
        if (model.getPluginsRoot() != null) {
            if (Files.exists(model.getPluginsRoot())) {
                roots.add(model.getPluginsRoot().toAbsolutePath().normalize());
            } else {
                warnings.add("Plugins root is invalid: " + model.getPluginsRoot());
            }
        }
        return new ResolvedComponentSources(roots, warnings);
    }

    private Document createEmptyConfigurationDocument() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        Document doc = factory.newDocumentBuilder().newDocument();

        Element rootElement = doc.createElementNS(CONFIG_NS, "chibiforgeConfiguration");
        rootElement.setAttribute("toolVersion", DEFAULT_TOOL_VERSION);
        rootElement.setAttribute("schemaVersion", DEFAULT_SCHEMA_VERSION);
        doc.appendChild(rootElement);

        Element targetsElement = doc.createElementNS(CONFIG_NS, "targets");
        Element targetElement = doc.createElementNS(CONFIG_NS, "target");
        targetElement.setAttribute("id", "default");
        targetsElement.appendChild(targetElement);
        rootElement.appendChild(targetsElement);

        Element componentsElement = doc.createElementNS(CONFIG_NS, "components");
        rootElement.appendChild(componentsElement);

        return doc;
    }

    /**
     * Show the top-level components card view.
     */
    private void showComponentsView() {
        if (model.getConfiguration() == null) {
            showWelcomeScreen();
            return;
        }
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
            Element componentsElement = findOrCreateComponentsElement();
            Document doc = componentsElement.getOwnerDocument();
            Element newComp = doc.createElementNS(componentsElement.getNamespaceURI(), "component");
            newComp.setAttribute("id", compId);
            componentsElement.appendChild(newComp);

            refreshConfigurationFromDocument();
            model.setModified(true);
            componentsView.refresh();
            palette.refresh();
            updateStatusBar();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Failed to add component: " + e.getMessage(), ButtonType.OK);
            alert.showAndWait();
        }
    }

    private void removeConfiguredComponent(String componentId) {
        var config = model.getConfiguration();
        if (config == null) return;

        try {
            for (ComponentConfigEntry entry : config.getComponents()) {
                if (entry.getComponentId().equals(componentId)) {
                    entry.getConfigElement().getParentNode().removeChild(entry.getConfigElement());
                    refreshConfigurationFromDocument();
                    model.setModified(true);
                    componentsView.refresh();
                    palette.refresh();
                    inspector.showConfigurationHelp();
                    updateStatusBar();
                    return;
                }
            }
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Failed to remove component: " + e.getMessage(), ButtonType.OK);
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

    private Element findOrCreateComponentsElement() {
        Element rootElement = model.getConfigurationRootElement();
        if (rootElement == null) {
            throw new IllegalStateException("No configuration document loaded");
        }
        var children = rootElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child) {
                String localName = child.getLocalName() != null ? child.getLocalName() : child.getTagName();
                if ("components".equals(localName)) {
                    return child;
                }
            }
        }
        Element componentsElement = rootElement.getOwnerDocument().createElementNS(CONFIG_NS, "components");
        rootElement.appendChild(componentsElement);
        return componentsElement;
    }

    private void refreshConfigurationFromDocument() throws Exception {
        Element rootElement = model.getConfigurationRootElement();
        if (rootElement == null) {
            throw new IllegalStateException("No configuration document loaded");
        }
        ConfigLoader loader = new ConfigLoader();
        ChibiForgeConfiguration config = loader.load(rootElement.getOwnerDocument());
        model.setConfiguration(config);
        updateTargets(config);
        refreshRegistryAndWarnings(model.getConfigFile(), config);
    }

    private void updateStatusBar() {
        var config = model.getConfiguration();
        var registry = model.getRegistry();
        if (config != null) {
            int compCount = config.getComponents().size();
            int regCount = registry != null ? registry.size() : 0;
            statusLeft.setText(compCount + " component(s) configured, " +
                    regCount + " available, target: " + model.getActiveTarget());
        } else {
            statusLeft.setText("No configuration loaded");
        }
        List<String> rightParts = new ArrayList<>();
        if (!model.getWarnings().isEmpty()) {
            rightParts.add(model.getWarnings().size() + " warning(s)");
        }
        if (model.getConfiguration() == null && !model.isModified()) {
            rightParts.add("Ready");
        } else {
            rightParts.add(model.isModified() ? "Modified" : "Saved");
        }
        statusRight.setText(String.join(" · ", rightParts));
    }

    private ResolvedComponentSources resolveComponentSources(Path configFile) {
        List<Path> preferredRoots = new ArrayList<>();
        if (model.getComponentsRoot() != null) {
            preferredRoots.add(model.getComponentsRoot());
        }
        if (model.getPluginsRoot() != null) {
            preferredRoots.add(model.getPluginsRoot());
        }
        return new ComponentSourceResolver().resolve(configFile, preferredRoots);
    }

    private boolean saveConfiguration() {
        if (model.getConfiguration() == null) return false;
        if (model.getConfigFile() == null) {
            return saveConfigurationAs();
        }

        try {
            Element docElement = model.getConfigurationRootElement();
            if (docElement == null) {
                return false;
            }

            // Collect exact text-property paths from all component schemas.
            Map<String, Set<String>> textPropertyPaths = collectTextPropertyPaths();

            XcfgWriter writer = new XcfgWriter();
            writer.save(docElement, model.getConfigFile(), textPropertyPaths);
            model.setModified(false);
            rememberRecentFile(model.getConfigFile());
            inspector.appendLog("Saved: " + model.getConfigFile());
            updateStatusBar();
            return true;
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Failed to save:\n" + e.getMessage(), ButtonType.OK);
            alert.showAndWait();
            return false;
        }
    }

    private boolean saveConfigurationAs() {
        if (model.getConfiguration() == null) return false;

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save ChibiForge Configuration File");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Configuration Files (*.xcfg)", "*.xcfg"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));

        Path currentFile = model.getConfigFile();
        if (currentFile != null) {
            if (currentFile.getParent() != null && Files.isDirectory(currentFile.getParent())) {
                chooser.setInitialDirectory(currentFile.getParent().toFile());
            }
            chooser.setInitialFileName(currentFile.getFileName().toString());
        } else {
            if (model.getConfigRoot() != null && Files.isDirectory(model.getConfigRoot())) {
                chooser.setInitialDirectory(model.getConfigRoot().toFile());
            }
            chooser.setInitialFileName("chibiforge.xcfg");
        }

        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return false;
        }

        Path outputPath = file.toPath().toAbsolutePath();
        Path outputRoot = outputPath.getParent() != null ? outputPath.getParent() : Path.of(".");
        try {
            Map<String, Set<String>> textPropertyPaths = collectTextPropertyPaths();
            XcfgWriter writer = new XcfgWriter();
            writer.save(model.getConfigurationRootElement(), outputPath, textPropertyPaths);
            model.setConfigFile(outputPath);
            model.setConfigRoot(outputRoot);
            refreshRegistryAndWarnings(outputPath, model.getConfiguration());
            rememberRecentFile(outputPath);
            model.setModified(false);
            inspector.appendLog("Saved: " + outputPath);
            inspector.refreshFiles();
            updateStatusBar();
            return true;
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Failed to save:\n" + e.getMessage(), ButtonType.OK);
            alert.showAndWait();
            return false;
        }
    }

    private Map<String, Set<String>> collectTextPropertyPaths() {
        Map<String, Set<String>> pathsByComponent = new HashMap<>();
        if (model.getRegistry() == null || model.getConfiguration() == null) return pathsByComponent;

        for (ComponentConfigEntry entry : model.getConfiguration().getComponents()) {
            try {
                ComponentContainer container = model.getRegistry().lookup(entry.getComponentId());
                ComponentDefinition def = container.loadDefinition();
                Set<String> paths = new HashSet<>();
                collectTextProperties(def.getSections(), "", paths);
                pathsByComponent.put(entry.getComponentId(), paths);
            } catch (Exception ignored) {
            }
        }
        return pathsByComponent;
    }

    private void collectTextProperties(List<SectionDef> sections, String basePath, Set<String> paths) {
        for (SectionDef section : sections) {
            String sectionPath = appendPath(basePath, IdNormalizer.normalize(section.getName()));
            for (Object child : section.getChildren()) {
                if (child instanceof PropertyDef prop) {
                    collectTextProperty(prop, sectionPath, paths);
                } else if (child instanceof LayoutDef layout) {
                    for (Object layoutChild : layout.getChildren()) {
                        if (layoutChild instanceof PropertyDef prop) {
                            collectTextProperty(prop, sectionPath, paths);
                        }
                    }
                }
            }
        }
    }

    private void collectTextProperty(PropertyDef prop, String basePath, Set<String> paths) {
        String propertyPath = appendPath(basePath, prop.getName());
        if (prop.getType() == PropertyDef.Type.TEXT) {
            paths.add(propertyPath);
        }
        if (!prop.getNestedSections().isEmpty()) {
            collectTextProperties(prop.getNestedSections(), appendPath(propertyPath, "*"), paths);
        }
    }

    private String appendPath(String basePath, String segment) {
        return basePath == null || basePath.isEmpty() ? segment : basePath + "/" + segment;
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
                if (!saveConfiguration()) {
                    return;
                }
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
                    List.copyOf(model.getResolvedComponentRoots()));

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

    private void openConfigurationDialog() {
        if (!confirmSafeToDiscardChanges("opening another configuration")) {
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Open ChibiForge Configuration File");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Configuration Files (*.xcfg)", "*.xcfg"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        Path initialDir = model.getConfigRoot();
        if (initialDir != null && Files.isDirectory(initialDir)) {
            chooser.setInitialDirectory(initialDir.toFile());
        }
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            openConfiguration(file.toPath());
        }
    }

    private void openRecentConfiguration(Path path) {
        if (!Files.exists(path)) {
            recentFiles.remove(path.toAbsolutePath().normalize());
            saveRecentFiles();
            rebuildRecentFilesMenu();
            showMissingRecentFileAlert(path);
            return;
        }
        if (!confirmSafeToDiscardChanges("opening another configuration")) {
            return;
        }
        openConfiguration(path);
    }

    private boolean confirmSafeToDiscardChanges(String action) {
        if (!model.isModified()) {
            return true;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Save changes before " + action + "?",
                new ButtonType("Save", ButtonBar.ButtonData.YES),
                new ButtonType("Don't Save", ButtonBar.ButtonData.NO),
                ButtonType.CANCEL);
        confirm.setHeaderText(null);
        var result = confirm.showAndWait();
        if (result.isEmpty() || result.get() == ButtonType.CANCEL) {
            return false;
        }
        if (result.get().getButtonData() == ButtonBar.ButtonData.YES) {
            return saveConfiguration();
        }
        return true;
    }

    private void showWelcomeScreen() {
        breadcrumb.setPath("Welcome");

        Label title = new Label("ChibiForge");
        title.getStyleClass().add("panel-header");

        Label subtitle = new Label("Open an existing configuration or create a new one.");
        subtitle.getStyleClass().add("placeholder-text");
        subtitle.setWrapText(true);
        subtitle.setTextAlignment(TextAlignment.CENTER);

        Button newButton = new Button("New Configuration");
        newButton.setOnAction(e -> requestNewConfiguration());

        Button openButton = new Button("Open Configuration...");
        openButton.setOnAction(e -> openConfigurationDialog());

        HBox actions = new HBox(12, newButton, openButton);
        actions.setAlignment(Pos.CENTER);

        VBox content = new VBox(16, title, subtitle, actions);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(32));

        if (!recentFiles.isEmpty()) {
            VBox recentBox = new VBox(8);
            recentBox.setAlignment(Pos.CENTER_LEFT);
            recentBox.setMaxWidth(500);

            Label recentLabel = new Label("Recent Files");
            recentLabel.getStyleClass().add("panel-header");
            recentBox.getChildren().add(recentLabel);

            for (Path recentFile : recentFiles) {
                Hyperlink link = new Hyperlink(recentFile.toString());
                link.setOnAction(e -> openRecentConfiguration(recentFile));
                recentBox.getChildren().add(link);
            }
            content.getChildren().add(recentBox);
        }

        centerPanel.getChildren().setAll(content);
        inspector.showConfigurationHelp();
        inspector.refreshFiles();
        updateStatusBar();
    }

    private void updateWindowTitle() {
        String title = "ChibiForge";
        if (model.getConfigFile() != null) {
            title += " - " + model.getConfigFile();
        } else if (model.getConfiguration() != null) {
            title += " - Untitled";
        }
        stage.setTitle(title);
    }

    private void loadRecentFiles() {
        recentFiles.clear();
        String stored = preferences.get(RECENT_FILES_KEY, "");
        if (stored.isBlank()) {
            return;
        }
        for (String value : stored.split("\n")) {
            if (!value.isBlank()) {
                recentFiles.add(Path.of(value));
            }
        }
    }

    private void saveRecentFiles() {
        preferences.put(RECENT_FILES_KEY, recentFiles.stream()
                .map(Path::toString)
                .reduce((left, right) -> left + "\n" + right)
                .orElse(""));
    }

    private void rememberRecentFile(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        recentFiles.remove(normalized);
        recentFiles.add(0, normalized);
        while (recentFiles.size() > MAX_RECENT_FILES) {
            recentFiles.remove(recentFiles.size() - 1);
        }
        saveRecentFiles();
        rebuildRecentFilesMenu();
    }

    private void rebuildRecentFilesMenu() {
        recentFilesMenu.getItems().clear();
        if (recentFiles.isEmpty()) {
            MenuItem emptyItem = new MenuItem("No recent files");
            emptyItem.setDisable(true);
            recentFilesMenu.getItems().add(emptyItem);
            recentFilesMenu.setDisable(true);
            return;
        }

        recentFilesMenu.setDisable(false);
        for (Path path : recentFiles) {
            MenuItem item = new MenuItem(path.toString());
            item.setOnAction(e -> openRecentConfiguration(path));
            recentFilesMenu.getItems().add(item);
        }
        recentFilesMenu.getItems().add(new SeparatorMenuItem());
        MenuItem clearItem = new MenuItem("Clear Menu");
        clearItem.setOnAction(e -> {
            recentFiles.clear();
            saveRecentFiles();
            rebuildRecentFilesMenu();
            if (model.getConfiguration() == null) {
                showWelcomeScreen();
            }
        });
        recentFilesMenu.getItems().add(clearItem);
    }

    private void showMissingRecentFileAlert(Path path) {
        Alert alert = new Alert(Alert.AlertType.WARNING,
                "The recent file is no longer available:\n" + path,
                ButtonType.OK);
        alert.setHeaderText(null);
        alert.setTitle("Recent File Missing");
        alert.showAndWait();
    }

    private MenuBar createMenuBar() {
        Menu fileMenu = new Menu("_File");
        MenuItem newItem = new MenuItem("New");
        newItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("Ctrl+N"));
        newItem.setOnAction(e -> requestNewConfiguration());

        MenuItem openItem = new MenuItem("Open...");
        openItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("Ctrl+O"));
        openItem.setOnAction(e -> openConfigurationDialog());
        MenuItem saveItem = new MenuItem("Save");
        saveItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("Ctrl+S"));
        saveItem.setOnAction(e -> saveConfiguration());
        MenuItem saveAsItem = new MenuItem("Save As...");
        saveAsItem.setOnAction(e -> saveConfigurationAs());

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> stage.close());

        fileMenu.getItems().addAll(
                newItem,
                openItem,
                recentFilesMenu,
                new SeparatorMenuItem(),
                saveItem,
                saveAsItem,
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
        Button newBtn = new Button("New");
        newBtn.setOnAction(e -> requestNewConfiguration());
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
                newBtn, saveBtn, sep1,
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
