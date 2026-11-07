package vallegrande.edu.pe.view;

import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import vallegrande.edu.pe.model.TableRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

final class DataTablePane extends VBox {
    private final Supplier<List<TableRow>> loader;
    private final TableView<TableRow> table = new TableView<>();
    private final TextField search = new TextField();
    private final HBox actions = new HBox(8);
    private final Label count = new Label();
    private final ComboBox<String> statusFilter = new ComboBox<>(FXCollections.observableArrayList("Activos", "Inactivos", "Todos"));
    private Consumer<TableRow> statusAction;
    private Predicate<TableRow> canChangeStatus = row -> true;
    private List<TableRow> source = new ArrayList<>();

    DataTablePane(String title, String description, Supplier<List<TableRow>> loader) {
        this.loader = loader;
        getStyleClass().add("module-page"); setSpacing(18); setPadding(new Insets(28,32,28,32));
        Label eyebrow = new Label("APRAQ · OPERACIÓN"); eyebrow.getStyleClass().add("eyebrow");
        Label heading = new Label(title); heading.getStyleClass().add("page-title");
        Label copy = new Label(description); copy.setWrapText(true); copy.getStyleClass().add("page-description");
        search.setPromptText("Buscar en los resultados…"); search.getStyleClass().add("search-field");
        Button reload = new Button("Actualizar"); reload.getStyleClass().add("quiet-button"); reload.setOnAction(event -> reload());
        Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
        actions.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        actions.getChildren().addAll(search,spacer,count,reload);
        table.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No hay registros para mostrar"));
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().addAll(eyebrow,heading,copy,actions,table);
        search.textProperty().addListener((obs,old,value)->filter(value));
        reload();
    }

    HBox actions(){ return actions; }
    TableView<TableRow> table(){ return table; }
    TableRow selected(){ return table.getSelectionModel().getSelectedItem(); }
    List<TableRow> allRows(){ return List.copyOf(source); }
    List<TableRow> visibleRows(){ return List.copyOf(table.getItems()); }
    void addAction(Button button){ actions.getChildren().add(0,button); }

    void enableStatusActions(Consumer<TableRow> action, Predicate<TableRow> allowed) {
        statusAction = action;
        canChangeStatus = allowed;
        statusFilter.setId("status-filter");
        statusFilter.setAccessibleText("Filtrar registros por estado");
        statusFilter.getSelectionModel().select("Activos");
        statusFilter.valueProperty().addListener((obs, old, value) -> filter(search.getText()));
        HBox filters = new HBox(8, new Label("Estado"), statusFilter);
        filters.setAlignment(Pos.CENTER_LEFT);
        actions.getChildren().add(1, filters);
        buildColumns();
        filter(search.getText());
    }

    void reload(){
        UiSupport.async(this,loader,rows->{source=new ArrayList<>(rows);buildColumns();filter(search.getText());});
    }

    private void buildColumns(){
        table.getColumns().clear();
        if (statusAction != null) {
            TableColumn<TableRow, TableRow> column = new TableColumn<>("Acciones");
            column.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
            column.setCellFactory(ignored -> new StatusActionCell());
            column.setSortable(false);
            column.setReorderable(false);
            column.setMinWidth(88);
            column.setMaxWidth(88);
            table.getColumns().add(column);
        }
        if(source.isEmpty())return;
        for(String key:source.get(0).values().keySet()){
            if(key.endsWith("_id")||key.equals("operation_key"))continue;
            TableColumn<TableRow,Object> column=new TableColumn<>(key.equals("id")?"N.º":key);
            column.setCellValueFactory(data->new ReadOnlyObjectWrapper<>(key.equals("Estado") && statusAction != null
                    ? statusLabel(data.getValue()) : data.getValue().get(key)));
            column.setPrefWidth(Math.max(110,Math.min(280,key.length()*11+70)));
            table.getColumns().add(column);
        }
    }

    private void filter(String value){
        String needle=value==null?"":value.toLowerCase(Locale.ROOT).trim();
        FilteredList<TableRow> filtered=new FilteredList<>(FXCollections.observableArrayList(source),row->matchesStatus(row)
                && (needle.isEmpty() || statusAction != null && statusLabel(row).toLowerCase(Locale.ROOT).contains(needle)
                || row.values().values().stream().anyMatch(v->v!=null&&String.valueOf(v).toLowerCase(Locale.ROOT).contains(needle))));
        table.setItems(filtered);count.setText(filtered.size()+" registro"+(filtered.size()==1?"":"s"));
    }

    private boolean matchesStatus(TableRow row) {
        if (statusAction == null || "Todos".equals(statusFilter.getValue())) return true;
        return ("Inactivos".equals(statusFilter.getValue()) ? "inactive" : "active").equals(row.get("Estado"));
    }

    private String statusLabel(TableRow row) {
        return switch (String.valueOf(row.get("Estado"))) {
            case "active" -> "Activo";
            case "inactive" -> "Inactivo";
            default -> UiSupport.text(row.get("Estado"));
        };
    }

    private final class StatusActionCell extends TableCell<TableRow, TableRow> {
        private final Button button = new Button();
        private final SVGPath icon = new SVGPath();
        private final Tooltip tooltip = new Tooltip();

        StatusActionCell() {
            setAlignment(Pos.CENTER);
            icon.getStyleClass().add("row-action-icon");
            icon.setMouseTransparent(true);
            button.setGraphic(icon);
            button.setTooltip(tooltip);
            button.setOnAction(event -> {
                TableRow row = getItem();
                if (row != null && canChangeStatus.test(row)) statusAction.accept(row);
            });
        }

        @Override protected void updateItem(TableRow row, boolean empty) {
            super.updateItem(row, empty);
            setText(null);
            if (empty || row == null || !("active".equals(row.get("Estado")) || "inactive".equals(row.get("Estado")))) {
                setGraphic(null);
                return;
            }
            boolean active = "active".equals(row.get("Estado"));
            boolean allowed = canChangeStatus.test(row);
            String label = active ? "Eliminar (cambiar a inactivo)" : "Restaurar (cambiar a activo)";
            button.getStyleClass().setAll("button", "row-action-button", active ? "delete-row-button" : "restore-row-button");
            icon.setContent(active
                    ? "M2 4 H14 M6 2 H10 L11 4 H5 Z M4 4 L5 14 H11 L12 4 M7 6 V12 M9 6 V12"
                    : "M2 2 V6 H6 M2.5 6 A6 6 0 1 1 2.5 11");
            button.setUserData(row.id("id"));
            button.setAccessibleText(label + " · registro #" + row.id("id"));
            tooltip.setText(allowed ? label : "No puede desactivar su propia cuenta");
            button.setDisable(!allowed);
            setGraphic(button);
        }
    }
}
