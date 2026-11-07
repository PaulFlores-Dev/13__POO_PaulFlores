package vallegrande.edu.pe.view;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import vallegrande.edu.pe.concurrent.DatabaseQueue;
import vallegrande.edu.pe.exception.UserMessages;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class UiSupport {
    private UiSupport() {}

    static <T> void async(Region busyTarget, Supplier<T> work, Consumer<T> success) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> async(busyTarget, work, success));
            return;
        }
        busyTarget.setDisable(true);
        busyTarget.setOpacity(.72);
        DatabaseQueue.submit(work).whenComplete((result, failure) ->
                Platform.runLater(() -> {
                    busyTarget.setDisable(false);
                    busyTarget.setOpacity(1);
                    if (failure != null) {
                        error("No se pudo completar la operación", rootMessage(failure));
                        return;
                    }
                    try {
                        success.accept(result);
                    } catch (RuntimeException exception) {
                        error("No se pudo mostrar la información", rootMessage(exception));
                    }
                }));
    }

    static void asyncRun(Region busyTarget, Runnable work, Runnable success) {
        async(busyTarget, () -> { work.run(); return null; }, ignored -> success.run());
    }

    static GridPane form() {
        GridPane grid = new GridPane();
        grid.setHgap(12); grid.setVgap(12); grid.setPadding(new Insets(8,0,8,0));
        return grid;
    }

    static void field(GridPane grid, int row, String label, Node control) {
        Label text = new Label(label); text.getStyleClass().add("field-label");
        grid.add(text,0,row); grid.add(control,1,row);
        GridPane.setHgrow(control, Priority.ALWAYS);
        if (control instanceof Region region) region.setMaxWidth(Double.MAX_VALUE);
    }

    static <T> Optional<T> dialog(String title, GridPane content, Supplier<T> result) {
        Dialog<T> dialog = new Dialog<>();
        dialog.setTitle(title); dialog.setHeaderText(title);
        applyStyles(dialog.getDialogPane());
        ButtonType saveType = new ButtonType("Guardar", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, cancelType);

        Label validation = new Label();
        validation.getStyleClass().add("validation-message");
        validation.setWrapText(true);
        validation.setMaxWidth(Double.MAX_VALUE);
        validation.setManaged(false);
        validation.setVisible(false);

        ScrollPane scroll = new ScrollPane(content); scroll.setFitToWidth(true); scroll.setMaxHeight(680); scroll.setStyle("-fx-background-color: transparent; -fx-background: transparent;");
        VBox body = new VBox(12, validation, scroll);
        dialog.getDialogPane().setContent(body); dialog.getDialogPane().setPrefWidth(680);

        AtomicReference<T> validatedResult = new AtomicReference<>();
        Node save = dialog.getDialogPane().lookupButton(saveType);
        save.getStyleClass().add("primary-button");
        save.addEventFilter(ActionEvent.ACTION, event -> {
            try {
                validatedResult.set(result.get());
                hideValidation(validation);
            } catch (RuntimeException exception) {
                event.consume();
                showValidation(validation, exception);
            }
        });
        dialog.setResultConverter(button -> button == saveType ? validatedResult.get() : null);
        try {
            return dialog.showAndWait();
        } catch (RuntimeException exception) {
            error("No se pudo abrir el formulario", rootMessage(exception));
            return Optional.empty();
        }
    }

    static void showValidation(Label validation, Throwable error) {
        validation.setText("Revise los datos: " + rootMessage(error));
        validation.setAccessibleText(validation.getText());
        validation.setManaged(true);
        validation.setVisible(true);
    }

    private static void hideValidation(Label validation) {
        validation.setText("");
        validation.setManaged(false);
        validation.setVisible(false);
    }

    static boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, new ButtonType("Confirmar", ButtonBar.ButtonData.OK_DONE));
        applyStyles(alert.getDialogPane());
        alert.setHeaderText(title); return alert.showAndWait().filter(button -> button.getButtonData()==ButtonBar.ButtonData.OK_DONE).isPresent();
    }

    static void info(String title,String message){Alert alert=new Alert(Alert.AlertType.INFORMATION,message,ButtonType.OK);applyStyles(alert.getDialogPane());alert.setHeaderText(title);alert.showAndWait();}
    static void error(String title,String message){Alert alert=new Alert(Alert.AlertType.ERROR,message,ButtonType.OK);applyStyles(alert.getDialogPane());alert.setHeaderText(title);alert.showAndWait();}
    private static void applyStyles(DialogPane pane){String css=UiSupport.class.getResource("/vallegrande/edu/pe/css/app.css").toExternalForm();if(!pane.getStylesheets().contains(css))pane.getStylesheets().add(css);}
    static String rootMessage(Throwable error){return UserMessages.from(error);}
    static BigDecimal decimal(String value,String label){try{return new BigDecimal(value.trim());}catch(Exception exception){throw new IllegalArgumentException(label+" debe ser un número válido");}}
    static Long nullableLong(Object value){return value==null?null:((Number)value).longValue();}
    static String text(Object value){return value==null?"":String.valueOf(value);}
}
