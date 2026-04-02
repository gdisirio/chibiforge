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

package org.chibios.chibiforge.ui.model;

import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.chibios.chibiforge.config.ChibiForgeConfiguration;
import org.chibios.chibiforge.config.ComponentConfigEntry;
import org.chibios.chibiforge.registry.ComponentRegistry;
import org.w3c.dom.Element;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Central application model holding the loaded configuration, registry,
 * and observable state for UI binding.
 */
public class AppModel {

    private final ObjectProperty<Path> configFile = new SimpleObjectProperty<>();
    private final ObjectProperty<Path> configRoot = new SimpleObjectProperty<>();
    private final ObjectProperty<ChibiForgeConfiguration> configuration = new SimpleObjectProperty<>();
    private final ObjectProperty<Element> configurationRootElement = new SimpleObjectProperty<>();
    private final ObjectProperty<ComponentRegistry> registry = new SimpleObjectProperty<>();
    private final StringProperty activeTarget = new SimpleStringProperty("default");
    private final BooleanProperty modified = new SimpleBooleanProperty(false);
    private final IntegerProperty validationErrorCount = new SimpleIntegerProperty(0);
    private final ObservableList<String> targets = FXCollections.observableArrayList();
    private final ObservableList<String> warnings = FXCollections.observableArrayList();
    private final ObservableList<String> unresolvedComponents = FXCollections.observableArrayList();
    private final ObservableList<Path> resolvedComponentRoots = FXCollections.observableArrayList();

    // Component sources (set via preferences or CLI args)
    private final ObjectProperty<Path> componentsRoot = new SimpleObjectProperty<>();
    private final ObjectProperty<Path> pluginsRoot = new SimpleObjectProperty<>();

    public Path getConfigFile() { return configFile.get(); }
    public void setConfigFile(Path path) { configFile.set(path); }
    public ObjectProperty<Path> configFileProperty() { return configFile; }

    public Path getConfigRoot() { return configRoot.get(); }
    public void setConfigRoot(Path path) { configRoot.set(path); }
    public ObjectProperty<Path> configRootProperty() { return configRoot; }

    public ChibiForgeConfiguration getConfiguration() { return configuration.get(); }
    public void setConfiguration(ChibiForgeConfiguration config) { configuration.set(config); }
    public ObjectProperty<ChibiForgeConfiguration> configurationProperty() { return configuration; }

    public Element getConfigurationRootElement() { return configurationRootElement.get(); }
    public void setConfigurationRootElement(Element rootElement) { configurationRootElement.set(rootElement); }
    public ObjectProperty<Element> configurationRootElementProperty() { return configurationRootElement; }

    public ComponentRegistry getRegistry() { return registry.get(); }
    public void setRegistry(ComponentRegistry reg) { registry.set(reg); }
    public ObjectProperty<ComponentRegistry> registryProperty() { return registry; }

    public String getActiveTarget() { return activeTarget.get(); }
    public void setActiveTarget(String target) { activeTarget.set(target); }
    public StringProperty activeTargetProperty() { return activeTarget; }

    public boolean isModified() { return modified.get(); }
    public void setModified(boolean mod) { modified.set(mod); }
    public BooleanProperty modifiedProperty() { return modified; }

    public int getValidationErrorCount() { return validationErrorCount.get(); }
    public void setValidationErrorCount(int count) { validationErrorCount.set(count); }
    public IntegerProperty validationErrorCountProperty() { return validationErrorCount; }

    public ObservableList<String> getTargets() { return targets; }
    public ObservableList<String> getWarnings() { return warnings; }
    public ObservableList<String> getUnresolvedComponents() { return unresolvedComponents; }
    public ObservableList<Path> getResolvedComponentRoots() { return resolvedComponentRoots; }

    public Path getComponentsRoot() { return componentsRoot.get(); }
    public void setComponentsRoot(Path path) { componentsRoot.set(path); }
    public ObjectProperty<Path> componentsRootProperty() { return componentsRoot; }

    public Path getPluginsRoot() { return pluginsRoot.get(); }
    public void setPluginsRoot(Path path) { pluginsRoot.set(path); }
    public ObjectProperty<Path> pluginsRootProperty() { return pluginsRoot; }

    /**
     * Returns the set of component IDs currently in the configuration.
     */
    public Set<String> getConfiguredComponentIds() {
        ChibiForgeConfiguration config = getConfiguration();
        if (config == null) return Set.of();
        return config.getComponents().stream()
                .map(ComponentConfigEntry::getComponentId)
                .collect(Collectors.toSet());
    }
}
