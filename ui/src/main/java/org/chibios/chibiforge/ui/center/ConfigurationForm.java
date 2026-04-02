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
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.chibios.chibiforge.component.*;
import org.chibios.chibiforge.config.ComponentConfigEntry;
import org.chibios.chibiforge.container.ComponentContainer;
import org.chibios.chibiforge.container.ComponentContent;
import org.chibios.chibiforge.datamodel.IdNormalizer;
import org.chibios.chibiforge.ui.model.AppModel;
import org.chibios.chibiforge.ui.model.LiveDataModel;
import org.chibios.chibiforge.ui.widgets.PropertyWidgetFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.InputStream;
import java.util.function.Consumer;

/**
 * Renders a component's configuration form from its schema.xml definition.
 * Sections become collapsible titled panes, properties become input widgets.
 * Supports layouts (GridPane), images (ImageView), and lists (TableView + drill-down).
 */
public class ConfigurationForm {

    private final AppModel model;
    private final VBox root;
    private final ScrollPane scrollPane;
    private final VBox formContent;
    private final PropertyWidgetFactory widgetFactory;
    private ComponentContent componentContent;

    // Callback for list drill-down navigation
    private Consumer<ListDrillDown> onListDrillDown;

    public ConfigurationForm(AppModel model) {
        this.model = model;
        this.widgetFactory = new PropertyWidgetFactory();
        widgetFactory.setOnDomUpdate(propName -> {
            model.setModified(true);
            widgetFactory.reEvaluateConditions();
        });

        formContent = new VBox();
        formContent.setSpacing(4);
        formContent.setPadding(new Insets(8));

        scrollPane = new ScrollPane(formContent);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);

        root = new VBox(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
    }

    public void setOnListDrillDown(Consumer<ListDrillDown> handler) {
        this.onListDrillDown = handler;
    }

    /**
     * Build the form for a specific component.
     */
    public void loadComponent(ComponentDefinition def, ComponentConfigEntry configEntry,
                              ComponentContainer container) {
        formContent.getChildren().clear();
        widgetFactory.clearBindings();
        this.componentContent = container.getComponentContent();

        try {
            LiveDataModel liveModel = new LiveDataModel(def, configEntry, container);
            widgetFactory.setLiveModel(liveModel);
        } catch (Exception e) {
            widgetFactory.setLiveModel(null);
        }

        // Component description
        if (def.getDescription() != null && !def.getDescription().isEmpty()) {
            Label desc = new Label(def.getDescription());
            desc.setWrapText(true);
            desc.getStyleClass().add("component-description");
            desc.setPadding(new Insets(0, 0, 8, 0));
            formContent.getChildren().add(desc);
        }

        Element configElement = configEntry.getConfigElement();

        for (SectionDef section : def.getSections()) {
            Node sectionNode = renderSection(section, configElement);
            formContent.getChildren().add(sectionNode);
        }

        // Initial evaluation of all @cond: expressions
        widgetFactory.reEvaluateConditions();
    }

    /**
     * Render a list item's nested sections (for drill-down editing).
     */
    public void loadListItem(PropertyDef listProp, Element itemElement) {
        formContent.getChildren().clear();
        widgetFactory.clearBindings();

        for (SectionDef section : listProp.getNestedSections()) {
            Node sectionNode = renderSection(section, itemElement);
            formContent.getChildren().add(sectionNode);
        }

        // Initial evaluation of all @cond: expressions in the drill-down view.
        widgetFactory.reEvaluateConditions();
    }

    private Node renderSection(SectionDef section, Element parentConfigElement) {
        VBox sectionContent = new VBox();
        sectionContent.setSpacing(4);
        sectionContent.setPadding(new Insets(4, 0, 4, 8));

        if (section.getDescription() != null && !section.getDescription().isEmpty()) {
            Label desc = new Label(section.getDescription());
            desc.setWrapText(true);
            desc.getStyleClass().add("section-description");
            desc.setPadding(new Insets(0, 0, 4, 0));
            sectionContent.getChildren().add(desc);
        }

        String normalizedName = IdNormalizer.normalize(section.getName());
        Element sectionElement = findOrCreateElement(parentConfigElement, normalizedName);

        for (Object child : section.getChildren()) {
            if (child instanceof PropertyDef prop) {
                if (prop.getType() == PropertyDef.Type.LIST) {
                    sectionContent.getChildren().add(
                            createListWidget(prop, sectionElement));
                } else {
                    sectionContent.getChildren().add(
                            widgetFactory.createPropertyRow(prop, sectionElement));
                }
            } else if (child instanceof LayoutDef layout) {
                sectionContent.getChildren().add(
                        renderLayout(layout, sectionElement));
            } else if (child instanceof ImageDef image) {
                sectionContent.getChildren().add(renderImage(image));
            }
        }

        TitledPane titledPane = new TitledPane(section.getName(), sectionContent);
        titledPane.setExpanded(section.isExpanded());
        titledPane.setAnimated(false);
        titledPane.getStyleClass().add("section-pane");

        // Section visibility
        if (!section.isVisible() && !section.hasVisibleCondition()) {
            titledPane.setVisible(false);
            titledPane.setManaged(false);
        }

        // Section editability — disable all children if not editable
        if (!section.isEditable() && !section.hasEditableCondition()) {
            sectionContent.setDisable(true);
        }

        // Register @cond: bindings for section-level visibility/editability
        widgetFactory.registerSectionConditions(section, titledPane, sectionContent);

        return titledPane;
    }

    private Node renderLayout(LayoutDef layout, Element sectionElement) {
        int cols = layout.getColumns();
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(8);
        grid.setPadding(new Insets(4, 0, 4, 0));

        // Set column constraints for equal-width columns
        for (int c = 0; c < cols; c++) {
            ColumnConstraints cc = new ColumnConstraints();
            cc.setPercentWidth(100.0 / cols);
            cc.setHgrow(Priority.ALWAYS);
            grid.getColumnConstraints().add(cc);
        }

        int row = 0, col = 0;
        for (Object child : layout.getChildren()) {
            Node node = null;
            if (child instanceof PropertyDef prop) {
                node = widgetFactory.createPropertyRow(prop, sectionElement);
            } else if (child instanceof ImageDef image) {
                node = renderImage(image);
            } else if (child instanceof LayoutDef.EmptySlot) {
                node = new Region(); // Empty cell
            }

            if (node != null) {
                grid.add(node, col, row);
                col++;
                if (col >= cols) {
                    col = 0;
                    row++;
                }
            }
        }

        // Apply alignment
        Pos alignment = switch (layout.getAlign()) {
            case "center" -> Pos.CENTER;
            case "right" -> Pos.CENTER_RIGHT;
            default -> Pos.CENTER_LEFT;
        };
        grid.setAlignment(alignment);

        return grid;
    }

    private Node renderImage(ImageDef imageDef) {
        VBox imageBox = new VBox(4);

        try {
            if (componentContent != null && componentContent.exists(imageDef.getFile())) {
                try (InputStream is = componentContent.open(imageDef.getFile())) {
                    Image image = new Image(is);
                    ImageView imageView = new ImageView(image);
                    imageView.setPreserveRatio(true);
                    imageView.setFitWidth(400);
                    imageBox.getChildren().add(imageView);
                }
            } else {
                Label missing = new Label("[Image: " + imageDef.getFile() + "]");
                missing.getStyleClass().add("placeholder-text");
                imageBox.getChildren().add(missing);
            }
        } catch (Exception e) {
            Label error = new Label("[Image error: " + imageDef.getFile() + "]");
            error.getStyleClass().add("placeholder-text");
            imageBox.getChildren().add(error);
        }

        if (imageDef.getText() != null) {
            Label caption = new Label(imageDef.getText());
            caption.getStyleClass().add("image-caption");
            imageBox.getChildren().add(caption);
        }

        Pos alignment = switch (imageDef.getAlign()) {
            case "center" -> Pos.CENTER;
            case "right" -> Pos.CENTER_RIGHT;
            default -> Pos.CENTER_LEFT;
        };
        imageBox.setAlignment(alignment);
        imageBox.setPadding(new Insets(4, 0, 4, 0));

        return imageBox;
    }

    private Node createListWidget(PropertyDef listProp, Element sectionElement) {
        VBox listBox = new VBox(4);
        listBox.setPadding(new Insets(4, 0, 4, 0));

        Element listElement = findOrCreateElement(sectionElement, listProp.getName());

        // Parse list_columns for table column definitions
        String[] colDefs = listProp.getListColumns() != null
                ? listProp.getListColumns().split(",") : new String[0];

        // Build the table
        javafx.scene.control.TableView<Element> table = new javafx.scene.control.TableView<>();
        table.setColumnResizePolicy(javafx.scene.control.TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPrefHeight(200);

        for (String colDef : colDefs) {
            String[] parts = colDef.trim().split(":");
            String propName = parts[0].trim();
            double width = parts.length > 1 ? Double.parseDouble(parts[1].trim()) : -1;

            javafx.scene.control.TableColumn<Element, String> column =
                    new javafx.scene.control.TableColumn<>(propName);
            column.setCellValueFactory(cellData -> {
                Element item = cellData.getValue();
                String value = getNestedValue(item, propName);
                return new javafx.beans.property.SimpleStringProperty(value);
            });
            if (width > 0) column.setPrefWidth(width);
            table.getColumns().add(column);
        }

        // Populate table with existing items
        refreshListTable(table, listElement);

        // Row controls
        javafx.scene.control.Button addBtn = new javafx.scene.control.Button("Add");
        javafx.scene.control.Button removeBtn = new javafx.scene.control.Button("Remove");
        javafx.scene.control.Button duplicateBtn = new javafx.scene.control.Button("Duplicate");
        javafx.scene.control.Button upBtn = new javafx.scene.control.Button("\u25B2");
        javafx.scene.control.Button downBtn = new javafx.scene.control.Button("\u25BC");

        addBtn.setOnAction(e -> {
            Element newItem = createDefaultItem(listElement, listProp);
            listElement.appendChild(newItem);
            refreshListTable(table, listElement);
            model.setModified(true);
        });

        removeBtn.setOnAction(e -> {
            Element selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                listElement.removeChild(selected);
                refreshListTable(table, listElement);
                model.setModified(true);
            }
        });

        duplicateBtn.setOnAction(e -> {
            Element selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                Element clone = (Element) selected.cloneNode(true);
                listElement.appendChild(clone);
                refreshListTable(table, listElement);
                model.setModified(true);
            }
        });

        upBtn.setOnAction(e -> {
            int idx = table.getSelectionModel().getSelectedIndex();
            if (idx > 0) {
                Element item = table.getItems().get(idx);
                Element prev = table.getItems().get(idx - 1);
                listElement.insertBefore(item, prev);
                refreshListTable(table, listElement);
                table.getSelectionModel().select(idx - 1);
                model.setModified(true);
            }
        });

        downBtn.setOnAction(e -> {
            int idx = table.getSelectionModel().getSelectedIndex();
            if (idx >= 0 && idx < table.getItems().size() - 1) {
                Element item = table.getItems().get(idx);
                Element next = table.getItems().get(idx + 1);
                // Insert next before item (effectively swaps them)
                listElement.insertBefore(next, item);
                refreshListTable(table, listElement);
                table.getSelectionModel().select(idx + 1);
                model.setModified(true);
            }
        });

        HBox controls = new HBox(4, addBtn, removeBtn, duplicateBtn,
                new javafx.scene.control.Separator(javafx.geometry.Orientation.VERTICAL),
                upBtn, downBtn);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPadding(new Insets(2, 0, 2, 0));

        // Double-click for drill-down
        table.setRowFactory(tv -> {
            javafx.scene.control.TableRow<Element> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    Element item = row.getItem();
                    int idx = table.getItems().indexOf(item);
                    if (onListDrillDown != null) {
                        onListDrillDown.accept(new ListDrillDown(
                                listProp, item, idx, table, listElement));
                    }
                }
            });
            return row;
        });

        // Item count label
        Label countLabel = new Label(table.getItems().size() + " item(s)");
        countLabel.getStyleClass().add("property-brief");

        // Header
        Label nameLabel = new Label(listProp.getName());
        nameLabel.getStyleClass().add("property-name");
        Label briefLabel = new Label(listProp.getBrief());
        briefLabel.getStyleClass().add("property-brief");
        briefLabel.setWrapText(true);

        HBox header = new HBox(8, nameLabel, countLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        listBox.getChildren().addAll(header, briefLabel, table, controls);
        return listBox;
    }

    private void refreshListTable(javafx.scene.control.TableView<Element> table, Element listElement) {
        table.getItems().clear();
        NodeList children = listElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el) {
                table.getItems().add(el);
            }
        }
    }

    /**
     * Get a nested property value from a list item element.
     * Searches through all child elements (section elements) for a match.
     */
    private String getNestedValue(Element item, String propName) {
        // Direct child
        NodeList direct = item.getElementsByTagName(propName);
        if (direct.getLength() > 0) {
            return direct.item(0).getTextContent().trim();
        }
        return "";
    }

    private Element createDefaultItem(Element listElement, PropertyDef listProp) {
        var doc = listElement.getOwnerDocument();
        Element item = doc.createElement("item");

        // Create nested section elements with default property values
        for (SectionDef section : listProp.getNestedSections()) {
            String sectionName = IdNormalizer.normalize(section.getName());
            Element sectionEl = doc.createElement(sectionName);
            item.appendChild(sectionEl);

            for (Object child : section.getChildren()) {
                if (child instanceof PropertyDef prop) {
                    Element propEl = doc.createElement(prop.getName());
                    propEl.setTextContent(prop.getDefaultValue() != null ? prop.getDefaultValue() : "");
                    sectionEl.appendChild(propEl);
                }
            }
        }

        return item;
    }

    private Element findOrCreateElement(Element parent, String name) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element el) {
                if (el.getTagName().equals(name) || el.getLocalName() != null && el.getLocalName().equals(name)) {
                    return el;
                }
            }
        }
        Element newEl = parent.getOwnerDocument().createElement(name);
        parent.appendChild(newEl);
        return newEl;
    }

    /**
     * Refresh all property widgets for a new active target.
     */
    public void refreshForTarget(String target) {
        widgetFactory.refreshForTarget(target);
    }

    public VBox getRoot() { return root; }
    public ScrollPane getScrollPane() { return scrollPane; }

    /**
     * Data class for list drill-down navigation.
     */
    public static class ListDrillDown {
        public final PropertyDef listProperty;
        public final Element itemElement;
        public final int itemIndex;
        public final javafx.scene.control.TableView<Element> table;
        public final Element listElement;

        public ListDrillDown(PropertyDef listProperty, Element itemElement, int itemIndex,
                             javafx.scene.control.TableView<Element> table, Element listElement) {
            this.listProperty = listProperty;
            this.itemElement = itemElement;
            this.itemIndex = itemIndex;
            this.table = table;
            this.listElement = listElement;
        }
    }
}
