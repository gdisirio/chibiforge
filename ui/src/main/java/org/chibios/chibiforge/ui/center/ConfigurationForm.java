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

package org.chibios.chibiforge.ui.center;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.VBox;
import org.chibios.chibiforge.component.*;
import org.chibios.chibiforge.config.ComponentConfigEntry;
import org.chibios.chibiforge.datamodel.IdNormalizer;
import org.chibios.chibiforge.ui.model.AppModel;
import org.chibios.chibiforge.ui.widgets.PropertyWidgetFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.util.List;

/**
 * Renders a component's configuration form from its schema.xml definition.
 * Sections become collapsible titled panes, properties become input widgets.
 */
public class ConfigurationForm {

    private final AppModel model;
    private final VBox root;
    private final ScrollPane scrollPane;
    private final VBox formContent;
    private final PropertyWidgetFactory widgetFactory;

    public ConfigurationForm(AppModel model) {
        this.model = model;
        this.widgetFactory = new PropertyWidgetFactory();
        widgetFactory.setOnDomUpdate(propName -> model.setModified(true));

        formContent = new VBox();
        formContent.setSpacing(4);
        formContent.setPadding(new Insets(8));

        scrollPane = new ScrollPane(formContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        root = new VBox(scrollPane);
    }

    /**
     * Build the form for a specific component.
     *
     * @param def         the component's schema definition
     * @param configEntry the component's configuration values from xcfg
     */
    public void loadComponent(ComponentDefinition def, ComponentConfigEntry configEntry) {
        formContent.getChildren().clear();

        // Component description
        if (def.getDescription() != null && !def.getDescription().isEmpty()) {
            Label desc = new Label(def.getDescription());
            desc.setWrapText(true);
            desc.getStyleClass().add("component-description");
            desc.setPadding(new Insets(0, 0, 8, 0));
            formContent.getChildren().add(desc);
        }

        Element configElement = configEntry.getConfigElement();

        // Render sections
        for (SectionDef section : def.getSections()) {
            Node sectionNode = renderSection(section, configElement);
            formContent.getChildren().add(sectionNode);
        }
    }

    private Node renderSection(SectionDef section, Element parentConfigElement) {
        VBox sectionContent = new VBox();
        sectionContent.setSpacing(4);
        sectionContent.setPadding(new Insets(4, 0, 4, 8));

        // Section description
        if (section.getDescription() != null && !section.getDescription().isEmpty()) {
            Label desc = new Label(section.getDescription());
            desc.setWrapText(true);
            desc.getStyleClass().add("section-description");
            desc.setPadding(new Insets(0, 0, 4, 0));
            sectionContent.getChildren().add(desc);
        }

        // Find or create the section element in the config DOM (normalized name)
        String normalizedName = IdNormalizer.normalize(section.getName());
        Element sectionElement = findOrCreateElement(parentConfigElement, normalizedName);

        // Render children
        for (Object child : section.getChildren()) {
            if (child instanceof PropertyDef prop) {
                Node row = widgetFactory.createPropertyRow(prop, sectionElement);
                sectionContent.getChildren().add(row);
            } else if (child instanceof LayoutDef layout) {
                // Layouts are M6 — render properties flat for now
                for (Object layoutChild : layout.getChildren()) {
                    if (layoutChild instanceof PropertyDef prop) {
                        Node row = widgetFactory.createPropertyRow(prop, sectionElement);
                        sectionContent.getChildren().add(row);
                    }
                }
            }
            // Images are M6
        }

        TitledPane titledPane = new TitledPane(section.getName(), sectionContent);
        titledPane.setExpanded(section.isExpanded());
        titledPane.setAnimated(false);
        titledPane.getStyleClass().add("section-pane");

        return titledPane;
    }

    /**
     * Find a child element by tag name, or create it if missing.
     * This maps section names to DOM elements in the xcfg.
     */
    private Element findOrCreateElement(Element parent, String name) {
        // Normalize: section names may have spaces, DOM elements use underscores/lowercase
        // But the xcfg uses the raw section names as element names
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el) {
                if (el.getTagName().equals(name) || el.getLocalName() != null && el.getLocalName().equals(name)) {
                    return el;
                }
            }
        }
        // Create if missing
        Element newEl = parent.getOwnerDocument().createElement(name);
        parent.appendChild(newEl);
        return newEl;
    }

    public VBox getRoot() { return root; }
    public ScrollPane getScrollPane() { return scrollPane; }
}
