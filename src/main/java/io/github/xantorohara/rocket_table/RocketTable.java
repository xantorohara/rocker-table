package io.github.xantorohara.rocket_table;

import io.github.xantorohara.rocket_table.engine.SmartColumns;
import io.github.xantorohara.rocket_table.engine.SmartRow;
import io.github.xantorohara.rocket_table.engine.SmartWatch;
import io.github.xantorohara.rocket_table.engine.TableModel;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ObservableMap;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.converter.DefaultStringConverter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static javafx.scene.input.KeyCombination.keyCombination;

@Slf4j
public class RocketTable {

    public Label columnsCountLabel;
    public Label selectedRowsLabel;
    public Label uniqueRowsLabel;
    public Label matchedRowsLabel;
    public Label totalRowsLabel;

    public Button openFileButton;
    public Button columnsSetButton;
    public Button exportButton;
    public TextField searchTextField;
    public TextField columnsTextField;
    public MenuItem truncateViewButton;
    public MenuItem truncateSelectionButton;
    public MenuButton truncateButton;
    public CheckBox filterCheckbox;
    public CheckBox uniqueCheckbox;
    public TableView<SmartRow> tableView;
    public ContextMenu columnsAutocompleteContextMenu;

    private FileChooser openFileChooser = new FileChooser();
    private FileChooser exportFileChooser = new FileChooser();

    private TableModel tableModel = new TableModel();
    private Stage stage;

    private boolean internalChange = false;

    @Setter
    private String encoding;

    @Setter
    private String sasDateFormatType;


    public void init(Stage stage) {
        log.info("Init started");
        this.stage = stage;

        tableView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        initFileChoosers();
        initHotkeys();
        initActions();
        initBindings();
        log.info("Init finished");
    }

    public void showAbout() {
        Alert dialog = new Alert(Alert.AlertType.INFORMATION);
        dialog.setTitle("About Rocket Table");
        dialog.initStyle(StageStyle.UTILITY);
        dialog.setHeaderText("\"Rocket table\" is a lightweight viewer for " +
                "SAS (.*sas7bdat), Spotfire (.sbdf, * stdf) and CSV files.\n" +
                "© Xantorohara, 2015-2021"
        );
        dialog.setGraphic(new ImageView((new Image(getClass().getResourceAsStream("icon.png")))));

        WebView webView = new WebView();
        webView.setPrefWidth(500);
        webView.setPrefHeight(300);
        WebEngine engine = webView.getEngine();
        engine.load(getClass().getResource("about.html").toExternalForm());
        dialog.getDialogPane().setContent(webView);
        dialog.showAndWait();
    }

    public void truncateView() {
        tableModel.truncate();

        internalChange = true;
        filterCheckbox.setSelected(false);
        tableModel.search(searchTextField.getText(), false);
        internalChange = false;

        repaintTable();
    }

    public void truncateSelection() {
        tableModel.truncate(tableView.getSelectionModel().getSelectedItems());

        internalChange = true;
        filterCheckbox.setSelected(false);
        tableModel.search(searchTextField.getText(), false);
        internalChange = false;

        repaintTable();
    }

    public void openFile() {
        File file = openFileChooser.showOpenDialog(stage);
        if (file != null) {
            openFile(file);
        }
    }

    public void exportData() {
        File file = exportFileChooser.showSaveDialog(exportButton.getScene().getWindow());
        if (file != null) {
            tableModel.export(file);
        }
    }

    public void setColumns() {
        if (tableModel.setColumns(columnsTextField.getText())) {
            recreateTable();
        }
    }

    public void filterRows() {
        tableModel.setFilterRows(filterCheckbox.isSelected());
        recreateTable();
    }

    public void uniqueRows() {
        tableModel.setUniqueRows(uniqueCheckbox.isSelected());
        recreateTable();
    }

    public void searchTextFieldAutocomplete(KeyEvent e) {
        if (e.isControlDown() && e.getCode() == KeyCode.SPACE) {
            columnsAutocomplete(searchTextField);
        }
    }

    public void columnsTextFieldAutocomplete(KeyEvent e) {
        if (e.isControlDown() && e.getCode() == KeyCode.SPACE) {
            columnsAutocomplete(columnsTextField);
        }
    }

    private void initFileChoosers() {
        openFileChooser.setInitialDirectory(new File("."));
        openFileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Supported Files", "*.sas7bdat", "*.sbdf", "*.stdf", "*.csv"),
                new FileChooser.ExtensionFilter("SAS File (SAS7BDAT)", "*.sas7bdat"),
                new FileChooser.ExtensionFilter("Spotfire Binary Data File (SBDF)", "*.sbdf"),
                new FileChooser.ExtensionFilter("Spotfire Text Data File (STDF)", "*.stdf"),
                new FileChooser.ExtensionFilter("CSV File", "*.csv")
        );

        exportFileChooser.setTitle("Export table data");
        exportFileChooser.setInitialDirectory(new File("."));
        exportFileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV file", "*.csv"));
    }

    private void initHotkeys() {
        ObservableMap<KeyCombination, Runnable> accelerators = stage.getScene().getAccelerators();
        accelerators.put(keyCombination("Alt+S"), searchTextField::requestFocus);
        accelerators.put(keyCombination("Alt+C"), columnsTextField::requestFocus);
        accelerators.put(keyCombination("Alt+O"), openFileButton::fire);
        accelerators.put(keyCombination("Alt+E"), exportButton::fire);
        accelerators.put(keyCombination("Alt+T"), truncateButton::fire);
        accelerators.put(keyCombination("Alt+U"), uniqueCheckbox::fire);
        accelerators.put(keyCombination("Alt+F"), filterCheckbox::fire);
        accelerators.put(keyCombination("Alt+D"), () -> {
            //todo some debug action
        });
    }

    private void initBindings() {
        truncateViewButton.disableProperty().bind(Bindings.isEmpty(tableModel.items));
        truncateSelectionButton.disableProperty().bind(Bindings.isEmpty(tableView.getSelectionModel().getSelectedItems()));

        uniqueRowsLabel.textProperty().bind(tableModel.uniqueRowCountProperty.asString());
        matchedRowsLabel.textProperty().bind(tableModel.matchedRowCountProperty.asString());
        selectedRowsLabel.textProperty().bind(Bindings.size(tableView.getSelectionModel().getSelectedCells()).asString());
        totalRowsLabel.textProperty().bind(tableModel.totalRowCountProperty.asString());
        columnsCountLabel.textProperty().bind(tableModel.totalColumnsProperty.asString());
    }

    private void initActions() {
        searchTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!internalChange) {
                tableModel.search(searchTextField.getText());
                repaintTable();
            }
        });
    }

    private void repaintTable() {
        tableView.getColumns().get(0).setVisible(false);
        tableView.getColumns().get(0).setVisible(true);
    }

    public void recreateTable() {
        log.info("recreateTable");

        List<TableColumn<SmartRow, String>> newColumns = new ArrayList<>();

        TableColumn<SmartRow, String> rownumTableColumn = new TableColumn<>("#");
        rownumTableColumn.setPrefWidth(30);

        rownumTableColumn.setCellFactory(column -> new TableCell<SmartRow, String>() {
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                } else {
                    setText(Integer.toString(getIndex() + 1));
                }
                getStyleClass().add("-xfx-rownum");
            }
        });
        newColumns.add(rownumTableColumn);

        SmartColumns columns = tableModel.getColumns();

        for (int i = 0; i < columns.getNames().length; i++) {

            TableColumn<SmartRow, String> tableColumn = new TableColumn<>(columns.getNames()[i]);

            if (columns.getType(i) == Number.class) {
                tableColumn.setComparator((o1, o2) -> {
                    try {
                        double d1 = o1.isEmpty() ? Double.MIN_VALUE : Double.parseDouble(o1);
                        double d2 = o2.isEmpty() ? Double.MIN_VALUE : Double.parseDouble(o2);
                        return Double.compare(d1, d2);
                    } catch (NumberFormatException ignored) {
                        return o1.compareTo(o2);
                    }
                });
            } else {
                tableColumn.setComparator(String::compareTo);
            }

            int j = i;
            tableColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getValue(j)));

            //tableColumn.setCellFactory(column -> new TableCell<SmartRow, String>() {
            tableColumn.setCellFactory(column -> new TextFieldTableCell<SmartRow, String>(new DefaultStringConverter()) {

                public void commitEdit(String newValue) {
                }

                public void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);

                    getStyleClass().removeAll("-xfx-cell-matched", "-xfx-row-matched");

                    if (item == null || empty) {
                        setText(null);
                        setGraphic(null);
                        return;
                    }

                    setText(item);

                    if (getIndex() > -1 && getIndex() < tableModel.items.size()) {
                        SmartRow row = tableModel.items.get(getIndex());

                        if (row.isMatched(j)) {
                            getStyleClass().add("-xfx-cell-matched");
                        } else if (row.isMatched()) {
                            getStyleClass().add("-xfx-row-matched");
                        }
                    }
                }
            });

            newColumns.add(tableColumn);
        }

        tableView.getColumns().setAll(newColumns);
        tableView.setItems(tableModel.items);
    }

    private void columnsAutocomplete(TextField textField) {
        String text = textField.getText();
        int caretPos = textField.getCaretPosition();
        String textBeforeCaret = caretPos > 0 ? text.substring(0, caretPos) : "";
        String textAfterCaret = text.substring(caretPos);
        int startPos = textBeforeCaret.trim().lastIndexOf(',');
        int beforeCommaPos = startPos == -1 ? 0 : startPos + 1;
        String prefix = textBeforeCaret.substring(beforeCommaPos).trim().toUpperCase();

        columnsAutocompleteContextMenu.getItems().clear();
        String[] columns = tableModel.getAllColumns().getNames();

        for (int i = 0; i < columns.length; i++) {
            String columnName = columns[i];
            if (prefix.isEmpty() || columnName.toUpperCase().startsWith(prefix)) {
                MenuItem mi = new MenuItem(columnName);
                mi.setOnAction(e -> {
                    String str = beforeCommaPos > 0 ? textBeforeCaret.substring(0, beforeCommaPos) + " " + columnName : columnName;

                    int newCaretPosition = str.length();

                    if (!textAfterCaret.isEmpty()) {
                        int afterPos1 = textAfterCaret.indexOf("=");
                        int afterPos2 = textAfterCaret.indexOf(",");

                        if (afterPos1 >= 0 && afterPos2 >= 0) {
                            str += textAfterCaret.substring(Math.min(afterPos1, afterPos2));
                        } else if (afterPos1 >= 0 || afterPos2 >= 0) {
                            str += textAfterCaret.substring(Math.max(afterPos1, afterPos2));
                        }
                    }
                    textField.setText(str);
                    textField.positionCaret(newCaretPosition);
                });
                columnsAutocompleteContextMenu.getItems().add(mi);
            }
        }
        columnsAutocompleteContextMenu.show(textField, Side.BOTTOM, 0, 0);
    }

    public void openFile(File file) {
        stage.setTitle(file.getName() + " - " + Const.VERSION);

        internalChange = true;
        searchTextField.clear();
        columnsTextField.clear();
        filterCheckbox.setSelected(false);
        internalChange = false;

        try {
            SmartWatch sw = SmartWatch.start("open");
            tableModel.load(file, encoding, sasDateFormatType);
            sw.stop();
            recreateTable();
        } catch (Exception e) {
            showExceptionDialog("Can't open file " + file.getName(), e);
            e.printStackTrace();
            tableView.getColumns().clear();
            tableView.getItems().clear();
        }
    }

    private void showExceptionDialog(String message, Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));

        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(message);
//        alert.setContentText(e.getMessage());

        TextArea textArea = new TextArea(sw.toString());
        textArea.setEditable(false);
        alert.getDialogPane().setExpandableContent(textArea);

        alert.showAndWait();
    }
}
