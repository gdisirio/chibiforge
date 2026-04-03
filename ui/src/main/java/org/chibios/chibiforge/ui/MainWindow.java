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
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.input.KeyCode;
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
import org.chibios.chibiforge.preset.PresetApplier;
import org.chibios.chibiforge.preset.PresetApplyReport;
import org.chibios.chibiforge.preset.PresetDefinition;
import org.chibios.chibiforge.preset.PresetLoader;
import org.chibios.chibiforge.preset.PresetWriter;
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
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.InputStream;
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
    private static final String OTHER_PRESET_OPTION = "Other...";

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
    private MenuItem newMenuItem;
    private MenuItem closeMenuItem;
    private MenuItem saveMenuItem;
    private MenuItem saveAsMenuItem;
    private MenuItem generateMenuItem;
    private MenuItem cleanMenuItem;
    private Button newToolButton;
    private Button closeToolButton;
    private Button saveToolButton;
    private Button generateToolButton;
    private Button cleanToolButton;
    private Button manageTargetsButton;
    private MenuItem loadPresetMenuItem;
    private MenuItem savePresetAsMenuItem;

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
    private final PresetLoader presetLoader = new PresetLoader();
    private final PresetApplier presetApplier = new PresetApplier();
    private final PresetWriter presetWriter = new PresetWriter();

    private record ComponentEditorContext(String componentId, ComponentContainer container,
                                          ComponentDefinition definition, ComponentConfigEntry configEntry) {
    }

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
        palette.setOnComponentActivated(this::handlePaletteActivation);
        palette.setOnSelectionChanged(this::handlePaletteSelection);

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
        componentsView.setOnComponentSelected(this::handleConfiguredComponentSelection);
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
        inspector.setOnOutlineSelect(this::handleOutlineSelection);

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
            if (e.getCode() == KeyCode.ESCAPE) {
                breadcrumb.navigateUp();
                e.consume();
            } else if (e.getCode() == KeyCode.F5) {
                runGenerate();
                e.consume();
            } else if (e.getCode() == KeyCode.DELETE) {
                if (handleDeleteShortcut()) {
                    e.consume();
                }
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
        model.configurationProperty().addListener((obs, old, config) -> updateStatusBar());
        model.configurationProperty().addListener((obs, old, config) -> updateActionState());
        model.registryProperty().addListener((obs, old, registry) -> updateStatusBar());
        model.activeTargetProperty().addListener((obs, old, target) -> updateStatusBar());
        model.validationErrorCountProperty().addListener((obs, old, count) -> updateStatusBar());
        model.getWarnings().addListener((javafx.collections.ListChangeListener<String>) change -> updateStatusBar());

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
        updateActionState();
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

    private void requestCloseConfiguration() {
        if (!confirmSafeToDiscardChanges("closing the current configuration")) {
            return;
        }
        closeConfiguration();
    }

    private void closeConfiguration() {
        lastViewedComponentId = null;
        model.setConfiguration(null);
        model.setConfigurationRootElement(null);
        model.setRegistry(null);
        model.setConfigFile(null);
        model.setConfigRoot(null);
        model.getTargets().setAll("default");
        model.setActiveTarget("default");
        model.getWarnings().clear();
        model.getUnresolvedComponents().clear();
        model.getResolvedComponentRoots().clear();
        model.setValidationErrorCount(0);
        model.setModified(false);
        targetSelector.getItems().setAll("default");
        targetSelector.getSelectionModel().select("default");
        palette.refresh();
        componentsView.refresh();
        showWelcomeScreen();
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
        configForm.clearView();
        breadcrumb.setPath("Components");
        centerPanel.getChildren().clear();
        centerPanel.getChildren().add(componentsView.getRoot());
        componentsView.refresh();
        inspector.showComponentsOutline();
        inspector.showConfigurationHelp();
        inspector.refreshFiles();
        updateStatusBar();
        updateActionState();
    }

    /**
     * Show the configuration form for a specific component.
     */
    private void showConfigurationForm(String componentId) {
        lastViewedComponentId = componentId;
        if (model.getRegistry() == null) {
            showUnresolvedComponentView(componentId,
                    "No component registry is available for the current configuration.");
            return;
        }
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
            updateActionState();
        } catch (Exception e) {
            showUnresolvedComponentView(componentId, e.getMessage());
        }
    }

    private void showUnresolvedComponentView(String componentId, String detail) {
        breadcrumb.setPath("Components", getComponentName(componentId));

        Label title = new Label("Component Source Not Resolved");
        title.getStyleClass().add("panel-header");

        Label summary = new Label("The component '" + componentId + "' is present in the configuration but could not be resolved from the current component roots.");
        summary.setWrapText(true);

        VBox content = new VBox(12, title, summary);
        content.setPadding(new Insets(16));

        if (!model.getResolvedComponentRoots().isEmpty()) {
            Label rootsHeader = new Label("Resolved component roots:");
            rootsHeader.getStyleClass().add("section-description");
            content.getChildren().add(rootsHeader);
            for (Path rootPath : model.getResolvedComponentRoots()) {
                Label rootLabel = new Label(rootPath.toString());
                rootLabel.setWrapText(true);
                content.getChildren().add(rootLabel);
            }
        }

        if (detail != null && !detail.isBlank()) {
            Label detailLabel = new Label("Details: " + detail);
            detailLabel.setWrapText(true);
            content.getChildren().add(detailLabel);
        }

        Label guidance = new Label("Add or correct component roots, then reopen the configuration.");
        guidance.setWrapText(true);
        content.getChildren().add(guidance);

        centerPanel.getChildren().setAll(content);
        configForm.clearView();
        inspector.showConfigurationHelp();
        inspector.refreshFiles();
        updateStatusBar();
        updateActionState();
    }

    /**
     * Add the component currently selected in the palette to the configuration.
     */
    private void addSelectedComponent() {
        addSelectedComponent(palette.getSelectedComponentId());
    }

    private void addSelectedComponent(String compId) {
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

    private void handlePaletteActivation(String componentId) {
        if (componentId == null) {
            return;
        }
        if (model.getConfiguredComponentIds().contains(componentId)) {
            showConfigurationForm(componentId);
        } else {
            addSelectedComponent(componentId);
        }
    }

    private void handlePaletteSelection(String componentId) {
        if (componentId == null || model.getRegistry() == null) {
            inspector.showConfigurationHelp();
            return;
        }
        try {
            ComponentDefinition def = model.getRegistry().lookup(componentId).loadDefinition();
            inspector.showComponentHelp(def);
        } catch (Exception ignored) {
            inspector.showConfigurationHelp();
        }
    }

    private void handleConfiguredComponentSelection(String componentId) {
        if (componentId == null || model.getRegistry() == null) {
            inspector.showConfigurationHelp();
            return;
        }
        try {
            ComponentDefinition def = model.getRegistry().lookup(componentId).loadDefinition();
            inspector.showComponentHelp(def);
        } catch (Exception ignored) {
            inspector.showConfigurationHelp();
        }
    }

    private void handleOutlineSelection(String target) {
        if (target == null || target.isBlank()) {
            return;
        }
        if (target.startsWith("component:")) {
            showConfigurationForm(target.substring("component:".length()));
            return;
        }
        if (lastViewedComponentId == null || model.getRegistry() == null) {
            return;
        }
        try {
            ComponentDefinition def = model.getRegistry().lookup(lastViewedComponentId).loadDefinition();
            if (target.startsWith("section:")) {
                String sectionName = target.substring("section:".length());
                SectionDef section = findSection(def.getSections(), sectionName);
                if (section != null) {
                    configForm.scrollToAnchor(sectionName);
                    inspector.showSectionHelp(section);
                }
            } else if (target.startsWith("property:")) {
                String propertyName = target.substring("property:".length());
                PropertyDef property = findProperty(def.getSections(), propertyName);
                if (property != null) {
                    configForm.scrollToAnchor(propertyName);
                    inspector.showPropertyHelp(property);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private SectionDef findSection(List<SectionDef> sections, String name) {
        for (SectionDef section : sections) {
            if (section.getName().equals(name)) {
                return section;
            }
        }
        return null;
    }

    private PropertyDef findProperty(List<SectionDef> sections, String name) {
        for (SectionDef section : sections) {
            for (Object child : section.getChildren()) {
                if (child instanceof PropertyDef prop) {
                    if (prop.getName().equals(name)) {
                        return prop;
                    }
                    PropertyDef nested = findProperty(prop.getNestedSections(), name);
                    if (nested != null) {
                        return nested;
                    }
                } else if (child instanceof LayoutDef layout) {
                    for (Object layoutChild : layout.getChildren()) {
                        if (layoutChild instanceof PropertyDef prop && prop.getName().equals(name)) {
                            return prop;
                        }
                    }
                }
            }
        }
        return null;
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
        if (config != null) {
            int compCount = config.getComponents().size();
            int propertyCount = countConfiguredProperties();
            statusLeft.setText(compCount + " component(s) · " +
                    propertyCount + " propert" + (propertyCount == 1 ? "y" : "ies") +
                    " · target: " + model.getActiveTarget());
        } else {
            statusLeft.setText("No configuration loaded");
        }
        List<String> rightParts = new ArrayList<>();
        if (!model.getWarnings().isEmpty()) {
            rightParts.add(model.getWarnings().size() + " warning(s)");
        }
        rightParts.add(model.getValidationErrorCount() + " validation error(s)");
        if (model.getConfiguration() == null && !model.isModified()) {
            rightParts.add("Ready");
        } else {
            rightParts.add(model.isModified() ? "Modified" : "Saved");
        }
        statusRight.setText(String.join(" · ", rightParts));
    }

    private int countConfiguredProperties() {
        if (model.getConfiguration() == null || model.getRegistry() == null) {
            return 0;
        }
        int total = 0;
        for (ComponentConfigEntry entry : model.getConfiguration().getComponents()) {
            try {
                ComponentContainer container = model.getRegistry().lookup(entry.getComponentId());
                ComponentDefinition def = container.loadDefinition();
                total += countProperties(def.getSections());
            } catch (Exception ignored) {
            }
        }
        return total;
    }

    private int countProperties(List<SectionDef> sections) {
        int total = 0;
        for (SectionDef section : sections) {
            for (Object child : section.getChildren()) {
                if (child instanceof PropertyDef prop) {
                    total += 1;
                    if (!prop.getNestedSections().isEmpty()) {
                        total += countProperties(prop.getNestedSections());
                    }
                } else if (child instanceof LayoutDef layout) {
                    for (Object layoutChild : layout.getChildren()) {
                        if (layoutChild instanceof PropertyDef prop) {
                            total += 1;
                            if (!prop.getNestedSections().isEmpty()) {
                                total += countProperties(prop.getNestedSections());
                            }
                        }
                    }
                }
            }
        }
        return total;
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
        dialog.showAndWait().ifPresent(result -> {
            try {
                applyTargetChanges(result);
            } catch (Exception e) {
                Alert alert = new Alert(Alert.AlertType.ERROR,
                        "Failed to update targets:\n" + e.getMessage(), ButtonType.OK);
                alert.showAndWait();
            }
        });
    }

    private void applyTargetChanges(ManageTargetsDialog.Result result) throws Exception {
        if (model.getConfigurationRootElement() == null) {
            return;
        }

        String remappedActiveTarget = result.remapTarget(model.getActiveTarget());
        rewriteTargetsElement(result.targets());
        rewriteTargetOverrides(model.getConfigurationRootElement(), result.renamedTargets(), Set.copyOf(result.deletedTargets()));
        normalizeMultiTargetProperties(model.getConfigurationRootElement());
        refreshConfigurationFromDocument();

        if (!model.getTargets().contains(remappedActiveTarget)) {
            remappedActiveTarget = "default";
        }
        model.setActiveTarget(remappedActiveTarget);
        targetSelector.getSelectionModel().select(remappedActiveTarget);
        configForm.refreshForTarget(remappedActiveTarget);
        model.setModified(true);

        if (centerPanel.getChildren().contains(configForm.getRoot()) && lastViewedComponentId != null) {
            showConfigurationForm(lastViewedComponentId);
        } else if (model.getConfiguration() != null) {
            showComponentsView();
        }
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
        configForm.clearView();
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
        updateActionState();
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
        newMenuItem = new MenuItem("New");
        newMenuItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("Ctrl+N"));
        newMenuItem.setOnAction(e -> requestNewConfiguration());

        MenuItem openItem = new MenuItem("Open...");
        openItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("Ctrl+O"));
        openItem.setOnAction(e -> openConfigurationDialog());
        closeMenuItem = new MenuItem("Close");
        closeMenuItem.setOnAction(e -> requestCloseConfiguration());
        saveMenuItem = new MenuItem("Save");
        saveMenuItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("Ctrl+S"));
        saveMenuItem.setOnAction(e -> saveConfiguration());
        saveAsMenuItem = new MenuItem("Save As...");
        saveAsMenuItem.setOnAction(e -> saveConfigurationAs());

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> stage.close());

        fileMenu.getItems().addAll(
                newMenuItem,
                openItem,
                closeMenuItem,
                recentFilesMenu,
                new SeparatorMenuItem(),
                saveMenuItem,
                saveAsMenuItem,
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
        loadPresetMenuItem = new MenuItem("Load Preset...");
        loadPresetMenuItem.setOnAction(e -> loadBundledPreset());
        savePresetAsMenuItem = new MenuItem("Save Preset As...");
        savePresetAsMenuItem.setOnAction(e -> savePresetAs());
        componentsMenu.getItems().addAll(
                new MenuItem("Add Component"),
                new MenuItem("Remove Component"),
                new SeparatorMenuItem(),
                loadPresetMenuItem,
                savePresetAsMenuItem
        );

        generateMenuItem = new MenuItem("Generate");
        generateMenuItem.setAccelerator(javafx.scene.input.KeyCombination.keyCombination("Ctrl+G"));
        generateMenuItem.setOnAction(e -> runGenerate());
        cleanMenuItem = new MenuItem("Clean");
        cleanMenuItem.setOnAction(e -> runClean());

        Menu generateMenu = new Menu("_Generate");
        generateMenu.getItems().addAll(generateMenuItem, cleanMenuItem);

        Menu helpMenu = new Menu("_Help");
        helpMenu.getItems().addAll(
                new MenuItem("About"),
                new MenuItem("Documentation")
        );

        return new MenuBar(fileMenu, editMenu, componentsMenu, generateMenu, helpMenu);
    }

    private boolean handleDeleteShortcut() {
        if (centerPanel.getChildren().contains(componentsView.getRoot()) && componentsView.hasSelection()) {
            componentsView.requestRemoveSelected();
            return true;
        }
        return false;
    }

    private Element findOrCreateTargetsElement() {
        Element rootElement = model.getConfigurationRootElement();
        if (rootElement == null) {
            throw new IllegalStateException("No configuration document loaded");
        }
        NodeList children = rootElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child && "targets".equals(localName(child))) {
                return child;
            }
        }
        Element targetsElement = rootElement.getOwnerDocument().createElementNS(CONFIG_NS, "targets");
        rootElement.insertBefore(targetsElement, findOrCreateComponentsElement());
        return targetsElement;
    }

    private void rewriteTargetsElement(List<String> targets) {
        Element targetsElement = findOrCreateTargetsElement();
        while (targetsElement.hasChildNodes()) {
            targetsElement.removeChild(targetsElement.getFirstChild());
        }
        Document doc = targetsElement.getOwnerDocument();
        String namespace = targetsElement.getNamespaceURI() != null ? targetsElement.getNamespaceURI() : CONFIG_NS;
        for (String target : targets) {
            Element targetElement = doc.createElementNS(namespace, "target");
            targetElement.setAttribute("id", target);
            targetsElement.appendChild(targetElement);
        }
    }

    private void rewriteTargetOverrides(Element root, Map<String, String> renamedTargets, Set<String> deletedTargets) {
        NodeList allElements = root.getElementsByTagName("*");
        List<Element> targetValueElements = new ArrayList<>();
        for (int i = 0; i < allElements.getLength(); i++) {
            if (allElements.item(i) instanceof Element element && "targetValue".equals(localName(element))) {
                targetValueElements.add(element);
            }
        }

        for (Element targetValueElement : targetValueElements) {
            String target = targetValueElement.getAttribute("target");
            if (deletedTargets.contains(target)) {
                targetValueElement.getParentNode().removeChild(targetValueElement);
                continue;
            }
            String renamed = renamedTargets.get(target);
            if (renamed != null) {
                targetValueElement.setAttribute("target", renamed);
            }
        }
    }

    private void normalizeMultiTargetProperties(Element root) {
        NodeList allElements = root.getElementsByTagName("*");
        List<Element> multiTargetProperties = new ArrayList<>();
        for (int i = 0; i < allElements.getLength(); i++) {
            if (allElements.item(i) instanceof Element element
                    && element.hasAttribute("default")
                    && !"targetValue".equals(localName(element))) {
                multiTargetProperties.add(element);
            }
        }

        for (Element propertyElement : multiTargetProperties) {
            boolean hasTargetValues = false;
            NodeList children = propertyElement.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i) instanceof Element child && "targetValue".equals(localName(child))) {
                    hasTargetValues = true;
                    break;
                }
            }
            if (!hasTargetValues) {
                org.chibios.chibiforge.ui.widgets.MultiTargetHelper.demoteToSingleTarget(propertyElement);
            }
        }
    }

    private String localName(Element element) {
        return element.getLocalName() != null ? element.getLocalName() : element.getTagName();
    }

    private ToolBar createToolBar() {
        newToolButton = new Button("New");
        newToolButton.setOnAction(e -> requestNewConfiguration());
        closeToolButton = new Button("Close");
        closeToolButton.setOnAction(e -> requestCloseConfiguration());
        saveToolButton = new Button("Save");
        saveToolButton.setOnAction(e -> saveConfiguration());
        Separator sep1 = new Separator();
        generateToolButton = new Button("Generate");
        generateToolButton.getStyleClass().add("accent-button");
        generateToolButton.setOnAction(e -> runGenerate());
        cleanToolButton = new Button("Clean");
        cleanToolButton.setOnAction(e -> runClean());
        Label targetLabel = new Label("Target:");

        manageTargetsButton = new Button("\u2699");
        manageTargetsButton.setTooltip(new Tooltip("Manage Targets..."));
        manageTargetsButton.setOnAction(e -> showManageTargetsDialog());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        return new ToolBar(
                newToolButton, closeToolButton, saveToolButton, sep1,
                generateToolButton, cleanToolButton,
                new Separator(),
                targetLabel, targetSelector, manageTargetsButton,
                spacer,
                inspectorToggle
        );
    }

    private void updateActionState() {
        boolean hasConfiguration = model.getConfiguration() != null;
        boolean hasComponentEditor = getCurrentComponentEditorContext() != null;

        if (newMenuItem != null) {
            newMenuItem.setDisable(!hasConfiguration);
        }
        if (saveMenuItem != null) {
            saveMenuItem.setDisable(!hasConfiguration);
        }
        if (closeMenuItem != null) {
            closeMenuItem.setDisable(!hasConfiguration);
        }
        if (saveAsMenuItem != null) {
            saveAsMenuItem.setDisable(!hasConfiguration);
        }
        if (generateMenuItem != null) {
            generateMenuItem.setDisable(!hasConfiguration);
        }
        if (cleanMenuItem != null) {
            cleanMenuItem.setDisable(!hasConfiguration);
        }
        if (newToolButton != null) {
            newToolButton.setDisable(!hasConfiguration);
        }
        if (saveToolButton != null) {
            saveToolButton.setDisable(!hasConfiguration);
        }
        if (closeToolButton != null) {
            closeToolButton.setDisable(!hasConfiguration);
        }
        if (generateToolButton != null) {
            generateToolButton.setDisable(!hasConfiguration);
        }
        if (cleanToolButton != null) {
            cleanToolButton.setDisable(!hasConfiguration);
        }
        if (manageTargetsButton != null) {
            manageTargetsButton.setDisable(!hasConfiguration);
        }
        if (targetSelector != null) {
            targetSelector.setDisable(!hasConfiguration);
        }
        if (loadPresetMenuItem != null) {
            loadPresetMenuItem.setDisable(!hasComponentEditor);
        }
        if (savePresetAsMenuItem != null) {
            savePresetAsMenuItem.setDisable(!hasComponentEditor);
        }
    }

    private ComponentEditorContext getCurrentComponentEditorContext() {
        if (lastViewedComponentId == null
                || model.getConfiguration() == null
                || model.getRegistry() == null
                || !centerPanel.getChildren().contains(configForm.getRoot())) {
            return null;
        }

        try {
            ComponentContainer container = model.getRegistry().lookup(lastViewedComponentId);
            ComponentDefinition definition = container.loadDefinition();
            for (ComponentConfigEntry entry : model.getConfiguration().getComponents()) {
                if (entry.getComponentId().equals(lastViewedComponentId)) {
                    return new ComponentEditorContext(lastViewedComponentId, container, definition, entry);
                }
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private void loadBundledPreset() {
        ComponentEditorContext context = getCurrentComponentEditorContext();
        if (context == null) {
            return;
        }

        try {
            List<String> bundledPresets = new ArrayList<>(context.container().listBundledPresets());
            bundledPresets.add(OTHER_PRESET_OPTION);

            ChoiceDialog<String> dialog = new ChoiceDialog<>(bundledPresets.get(0), bundledPresets);
            dialog.setTitle("Load Bundled Preset");
            dialog.setHeaderText("Select a bundled preset for " + context.definition().getName());
            dialog.setContentText("Preset:");
            dialog.showAndWait().ifPresent(selected -> {
                if (OTHER_PRESET_OPTION.equals(selected)) {
                    loadExternalPreset();
                    return;
                }
                try (InputStream input = context.container().openBundledPreset(selected)) {
                    PresetDefinition preset = presetLoader.load(input);
                    applyPreset(context, preset, "bundled preset '" + selected + "'");
                } catch (Exception e) {
                    showError("Failed to load preset", e.getMessage());
                }
            });
        } catch (Exception e) {
            showError("Failed to list bundled presets", e.getMessage());
        }
    }

    private void loadExternalPreset() {
        ComponentEditorContext context = getCurrentComponentEditorContext();
        if (context == null) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Load Preset");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Preset Files (*.xml)", "*.xml"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        Path initialDir = model.getConfigRoot();
        if (initialDir != null && Files.isDirectory(initialDir)) {
            chooser.setInitialDirectory(initialDir.toFile());
        }
        File file = chooser.showOpenDialog(stage);
        if (file == null) {
            return;
        }

        try {
            PresetDefinition preset = presetLoader.load(file.toPath());
            applyPreset(context, preset, "preset '" + file.toPath().toAbsolutePath() + "'");
        } catch (Exception e) {
            showError("Failed to load preset", e.getMessage());
        }
    }

    private void savePresetAs() {
        ComponentEditorContext context = getCurrentComponentEditorContext();
        if (context == null) {
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save Preset As");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Preset Files (*.xml)", "*.xml"),
                new FileChooser.ExtensionFilter("All Files", "*.*"));
        Path initialDir = model.getConfigRoot();
        if (initialDir != null && Files.isDirectory(initialDir)) {
            chooser.setInitialDirectory(initialDir.toFile());
        }
        chooser.setInitialFileName(defaultPresetFilename(context));
        File file = chooser.showSaveDialog(stage);
        if (file == null) {
            return;
        }

        String presetName = presetNameFromFile(file.toPath());
        if (presetName.isBlank()) {
            showError("Invalid preset name", "Preset filename must not be blank.");
            return;
        }

        try {
            presetWriter.save(presetName, context.definition(), context.configEntry().getConfigElement(),
                    model.getActiveTarget(), file.toPath().toAbsolutePath());
            inspector.appendLog("Saved preset: " + file.toPath().toAbsolutePath());
            showInfo("Preset saved", "Preset exported to:\n" + file.toPath().toAbsolutePath());
        } catch (Exception e) {
            showError("Failed to save preset", e.getMessage());
        }
    }

    private void applyPreset(ComponentEditorContext context, PresetDefinition preset, String sourceDescription) throws Exception {
        if (componentHasConfiguredValues(context.configEntry().getConfigElement())) {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Applying the preset will update the current component values. Continue?",
                    ButtonType.OK, ButtonType.CANCEL);
            confirm.setHeaderText(null);
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                return;
            }
        }

        PresetApplier.ApplyOptions options = resolvePresetApplyOptions();
        if (options == null) {
            return;
        }

        PresetApplyReport report = presetApplier.apply(
                preset, context.definition(), context.configEntry().getConfigElement(), options);
        refreshConfigurationFromDocument();
        appendPresetWarnings(report.warnings());
        model.setModified(true);
        showConfigurationForm(context.componentId());

        inspector.showLogTab();
        inspector.appendLog("Applied " + sourceDescription + " to " + context.definition().getName());
        inspector.appendLog("Preset applied: " + report.updatedCount() + " updated, "
                + report.ignoredCount() + " ignored, " + report.unchangedCount() + " unchanged.");
        for (String warning : report.warnings()) {
            inspector.appendLog(warning);
        }
        updateStatusBar();
    }

    private PresetApplier.ApplyOptions resolvePresetApplyOptions() {
        String activeTarget = model.getActiveTarget() != null ? model.getActiveTarget() : "default";
        if ("default".equals(activeTarget)) {
            return new PresetApplier.ApplyOptions(activeTarget, false);
        }

        Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
        ButtonType applyToDefault = new ButtonType("Write To Default", ButtonBar.ButtonData.NO);
        ButtonType createOverrides = new ButtonType("Create Overrides", ButtonBar.ButtonData.YES);
        dialog.getButtonTypes().setAll(createOverrides, applyToDefault, ButtonType.CANCEL);
        dialog.setTitle("Preset Target Handling");
        dialog.setHeaderText("Apply preset to target '" + activeTarget + "'");
        dialog.setContentText("Choose whether inherited scalar values should create explicit target overrides.");
        ButtonType result = dialog.showAndWait().orElse(ButtonType.CANCEL);
        if (result == ButtonType.CANCEL) {
            return null;
        }
        return new PresetApplier.ApplyOptions(activeTarget, result == createOverrides);
    }

    private boolean componentHasConfiguredValues(Element componentElement) {
        NodeList descendants = componentElement.getElementsByTagName("*");
        for (int i = 0; i < descendants.getLength(); i++) {
            if (descendants.item(i) instanceof Element element) {
                if ("component".equals(localName(element)) || "targetValue".equals(localName(element))) {
                    continue;
                }
                if (element.hasAttribute("default")) {
                    return true;
                }
                if (!element.getTextContent().trim().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void appendPresetWarnings(List<String> presetWarnings) {
        if (presetWarnings == null || presetWarnings.isEmpty()) {
            return;
        }
        for (String warning : presetWarnings) {
            if (!model.getWarnings().contains(warning)) {
                model.getWarnings().add(warning);
            }
        }
    }

    private String defaultPresetName(ComponentEditorContext context) {
        String target = model.getActiveTarget() != null ? model.getActiveTarget() : "default";
        return context.definition().getName() + " (" + target + ")";
    }

    private String defaultPresetFilename(ComponentEditorContext context) {
        String target = model.getActiveTarget() != null ? model.getActiveTarget() : "default";
        return IdNormalizer.normalize(context.definition().getName()) + "_" + target + ".xml";
    }

    private String presetNameFromFile(Path file) {
        String filename = file.getFileName() != null ? file.getFileName().toString().trim() : "";
        int extensionIndex = filename.lastIndexOf('.');
        if (extensionIndex > 0) {
            return filename.substring(0, extensionIndex).trim();
        }
        return filename;
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
    }

    private void showInfo(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.showAndWait();
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
