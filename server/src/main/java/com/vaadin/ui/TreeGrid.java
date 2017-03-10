/*
 * Copyright 2000-2016 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.jsoup.nodes.Attributes;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.vaadin.data.HierarchyData;
import com.vaadin.data.provider.DataProvider;
import com.vaadin.data.provider.HierarchicalDataCommunicator;
import com.vaadin.data.provider.HierarchicalDataProvider;
import com.vaadin.data.provider.HierarchicalQuery;
import com.vaadin.data.provider.InMemoryHierarchicalDataProvider;
import com.vaadin.shared.ui.treegrid.NodeCollapseRpc;
import com.vaadin.shared.ui.treegrid.TreeGridState;
import com.vaadin.ui.declarative.DesignAttributeHandler;
import com.vaadin.ui.declarative.DesignContext;
import com.vaadin.ui.declarative.DesignFormatter;

/**
 * A grid component for displaying hierarchical tabular data.
 *
 * @author Vaadin Ltd
 * @since 8.1
 *
 * @param <T>
 *            the grid bean type
 */
public class TreeGrid<T> extends Grid<T> {

    public TreeGrid() {
        super(new HierarchicalDataCommunicator<>());

        registerRpc(new NodeCollapseRpc() {
            @Override
            public void toggleCollapse(String rowKey, int rowIndex,
                    boolean collapse) {
                if (collapse) {
                    getDataCommunicator().doCollapse(rowKey, rowIndex);
                } else {
                    getDataCommunicator().doExpand(rowKey, rowIndex);
                }
            }
        });
    }

    @Override
    public void setItems(Collection<T> items) {
        setDataProvider(new InMemoryHierarchicalDataProvider<>(
                new HierarchyData<T>().addItems(null, items)));
    }

    @Override
    public void setItems(Stream<T> items) {
        setDataProvider(new InMemoryHierarchicalDataProvider<>(
                new HierarchyData<T>().addItems(null, items)));
    }

    @Override
    public void setItems(T... items) {
        setDataProvider(new InMemoryHierarchicalDataProvider<>(
                new HierarchyData<T>().addItems(null, items)));
    }

    @Override
    public void setDataProvider(DataProvider<T, ?> dataProvider) {
        if (!(dataProvider instanceof HierarchicalDataProvider)) {
            throw new IllegalArgumentException(
                    "TreeGrid only accepts hierarchical data providers");
        }
        super.setDataProvider(dataProvider);
    }

    /**
     * Set the column that displays the hierarchy of this grid's data. By
     * default the hierarchy will be displayed in the first column.
     * <p>
     * Setting a hierarchy column by calling this method also sets the column to
     * be visible and not hidable.
     *
     * @see Column#setId(String)
     *
     * @param id
     *            id of the column to use for displaying hierarchy
     */
    public void setHierarchyColumn(String id) {
        Objects.requireNonNull(id, "id may not be null");
        if (getColumn(id) == null) {
            throw new IllegalArgumentException("No column found for given id");
        }
        getColumn(id).setHidden(false);
        getColumn(id).setHidable(false);
        getState().hierarchyColumnId = getInternalIdForColumn(getColumn(id));
    }

    @Override
    protected TreeGridState getState() {
        return (TreeGridState) super.getState();
    }

    @Override
    protected TreeGridState getState(boolean markAsDirty) {
        return (TreeGridState) super.getState(markAsDirty);
    }

    @Override
    public HierarchicalDataCommunicator<T> getDataCommunicator() {
        return (HierarchicalDataCommunicator<T>) super.getDataCommunicator();
    }

    @Override
    public HierarchicalDataProvider<T, ?> getDataProvider() {
        if (!(super.getDataProvider() instanceof HierarchicalDataProvider)) {
            return null;
        }
        return (HierarchicalDataProvider<T, ?>) super.getDataProvider();
    }

    @Override
    protected void doReadDesign(Element design, DesignContext context) {
        super.doReadDesign(design, context);
        Attributes attrs = design.attributes();
        if (attrs.hasKey("hierarchy-column")) {
            setHierarchyColumn(DesignAttributeHandler
                    .readAttribute("hierarchy-column", attrs, String.class));
        }
    }

    @Override
    protected void readData(Element body,
            List<DeclarativeValueProvider<T>> providers) {
        getSelectionModel().deselectAll();
        List<T> selectedItems = new ArrayList<>();
        HierarchyData<T> data = new HierarchyData<T>();

        for (Element row : body.children()) {
            T item = deserializeDeclarativeRepresentation(row.attr("item"));
            T parent = null;
            if (row.hasAttr("parent")) {
                parent = deserializeDeclarativeRepresentation(
                        row.attr("parent"));
            }
            data.addItem(parent, item);
            if (row.hasAttr("selected")) {
                selectedItems.add(item);
            }
            Elements cells = row.children();
            int i = 0;
            for (Element cell : cells) {
                providers.get(i).addValue(item, cell.html());
                i++;
            }
        }

        setDataProvider(new InMemoryHierarchicalDataProvider<>(data));
        selectedItems.forEach(getSelectionModel()::select);
    }

    @Override
    protected void doWriteDesign(Element design, DesignContext designContext) {
        super.doWriteDesign(design, designContext);
        if (getColumnByInternalId(getState(false).hierarchyColumnId) != null) {
            String hierarchyColumn = getColumnByInternalId(
                    getState(false).hierarchyColumnId).getId();
            DesignAttributeHandler.writeAttribute("hierarchy-column",
                    design.attributes(), hierarchyColumn, null, String.class,
                    designContext);
        }
    }

    @Override
    protected void writeData(Element body, DesignContext designContext) {
        getDataProvider().fetch(new HierarchicalQuery<>(null, null))
                .forEach(item -> writeRow(body, item, null, designContext));
    }

    private void writeRow(Element container, T item, T parent,
            DesignContext context) {
        Element tableRow = container.appendElement("tr");
        tableRow.attr("item", serializeDeclarativeRepresentation(item));
        if (parent != null) {
            tableRow.attr("parent", serializeDeclarativeRepresentation(parent));
        }
        if (getSelectionModel().isSelected(item)) {
            tableRow.attr("selected", "");
        }
        for (Column<T, ?> column : getColumns()) {
            Object value = column.getValueProvider().apply(item);
            tableRow.appendElement("td")
                    .append(Optional.ofNullable(value).map(Object::toString)
                            .map(DesignFormatter::encodeForTextNode)
                            .orElse(""));
        }
        getDataProvider().fetch(new HierarchicalQuery<>(null, item))
                .forEach(childItem -> writeRow(container, childItem, item,
                        context));
    }
}