package org.example.csvquery;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import org.example.csvquery.models.Lexer;
import org.example.csvquery.models.Token;

import java.io.*;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.*;

public class MainController implements Initializable {

    // ── Root ─────────────────────────────────────────────────
    @FXML private BorderPane rootPane;

    // ── Topbar ───────────────────────────────────────────────
    @FXML private Button btnTema;

    // ── Editor ───────────────────────────────────────────────
    @FXML private TextArea editorSQL;
    @FXML private VBox     lineNumbers;

    // ── Status bar ───────────────────────────────────────────
    @FXML private Label lblEstado;
    @FXML private Label lblArchivo;
    @FXML private Label lblColumnas;
    @FXML private Label lblEncoding;
    @FXML private Label lblCursor;

    // ── Result tabs ──────────────────────────────────────────
    @FXML private Button tabResultado;
    @FXML private Button tabTokens;
    @FXML private Button tabConsola;

    // ── Results table ────────────────────────────────────────
    @FXML private TableView<ObservableList<String>> tablaResultados;

    // ── Dictionary panel ─────────────────────────────────────
    @FXML private Label lblFilas;
    @FXML private Label lblPeso;

    // ── Estado interno ───────────────────────────────────────
    private File         archivoCSV;
    private boolean      temaClaro      = false;
    private List<Token>  ultimosTokens  = new ArrayList<>();
    private List<String> logConsola     = new ArrayList<>();
    private String       ultimoScriptPy = "";

    // ruta de la matriz de transicion
    private static final String RUTA_AUTOMATA =
            "src/main/resources/CSV/Matriz de transicion 2.csv";

    // ══════════════════════════════════════════════════════════
    //  INICIALIZACIÓN
    // ══════════════════════════════════════════════════════════

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        editorSQL.setText(
                "-- Escribe tu consulta aquí\n\n" +
                        "TRAER nombre, edad DESDE \"datos.csv\" DONDE edad > 20;"
        );
        sincronizarNumerosLinea();
        sincronizarCursor();
        setEstado("LISTO", "lbl-estado");
        log("INFO", "Aplicación iniciada.");
        log("INFO", "Autómata esperado en: " + RUTA_AUTOMATA);
    }

    // ══════════════════════════════════════════════════════════
    //  TEMA
    // ══════════════════════════════════════════════════════════

    @FXML
    public void onToggleTema() {
        temaClaro = !temaClaro;
        ObservableList<String> clases = rootPane.getStyleClass();
        if (temaClaro) {
            if (!clases.contains("theme-light")) clases.add("theme-light");
            btnTema.setText("🌙 Tema Oscuro");
        } else {
            clases.remove("theme-light");
            btnTema.setText("☀ Tema Claro");
        }
    }

    // ══════════════════════════════════════════════════════════
    //  TOPBAR
    // ══════════════════════════════════════════════════════════

    @FXML
    public void onAbrirCSV() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Abrir archivo CSV");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Archivos CSV", "*.csv")
        );
        Stage stage = (Stage) editorSQL.getScene().getWindow();
        File file = chooser.showOpenDialog(stage);
        if (file != null) {
            archivoCSV = file;
            lblArchivo.setText(file.getName().toUpperCase());
            lblPeso.setText(fmtBytes(file.length()));
            log("INFO", "Archivo seleccionado: " + file.getAbsolutePath());
            cargarEncabezados(file);
            // Ruta absoluta con barras hacia adelante para Python
            String ruta = file.getAbsolutePath().replace("\\", "/");
            editorSQL.setText(
                    "-- Ejecutando consulta local\n\n" +
                            "TRAER * DESDE \"" + ruta + "\";"
            );
        }
    }

    @FXML
    public void onLimpiarConsola() {
        editorSQL.clear();
        tablaResultados.getItems().clear();
        tablaResultados.getColumns().clear();
        ultimosTokens.clear();
        logConsola.clear();
        ultimoScriptPy = "";
        setEstado("LISTO", "lbl-estado");
        lblCursor.setText("LÍNEA 1, COL 1");
        actualizarNumerosLinea(1);
    }

    @FXML
    public void onEjecutarConsulta() {
        String texto = editorSQL.getText().trim();

        if (texto.isEmpty()) {
            alerta("Consulta vacía", "Escribe una consulta antes de ejecutar.");
            return;
        }

        setEstado("ANALIZANDO...", "lbl-estado-running");
        log("INFO", "Iniciando análisis léxico...");

        // Escribir consulta en archivo temporal para el Lexer ────
        File tempQuery = null;
        try {
            tempQuery = File.createTempFile("csvquery_input_", ".txt");
            tempQuery.deleteOnExit();
            String sinComentarios = texto.replaceAll("--[^\n]*", "").trim();
            try (FileWriter fw = new FileWriter(tempQuery)) {
                fw.write(sinComentarios);
            }
        } catch (IOException e) {
            alerta("Error", "No se pudo crear archivo temporal: " + e.getMessage());
            setEstado("ERROR", "lbl-estado-error");
            return;
        }

        // Lexer con autómata ─────────────────────────────────────
        Lexer lexer = new Lexer(RUTA_AUTOMATA);
        lexer.analizarArchivo(tempQuery.getAbsolutePath());
        tempQuery.delete();

        ultimosTokens = lexer.getTablaSimbolos();
        List<Token> errores = lexer.getPilaErrores();

        log("INFO", "Tokens reconocidos: " + ultimosTokens.size());

        if (!errores.isEmpty()) {
            log("ERROR", "Errores léxicos detectados: " + errores.size());
            for (Token e : errores)
                log("ERROR", "Token inválido: '" + e.getLexema() + "'");
            alerta("Error léxico",
                    "Se encontraron " + errores.size() + " errores léxicos.\n" +
                            "Revisa la pestaña 'Consola de Compilación'.");
            setEstado("ERROR LÉXICO", "lbl-estado-error");
            mostrarConsolaEnTabla();
            return;
        }

        //Traducir tokens → script Python/pandas ─────────────────
        setEstado("GENERANDO SCRIPT...", "lbl-estado-running");
        PandasTranslator translator = new PandasTranslator(ultimosTokens);
        try {
            ultimoScriptPy = translator.traducir();
            log("INFO", "Script Python generado:");
            for (String linea : ultimoScriptPy.split("\n"))
                log("SCRIPT", linea);
        } catch (IllegalArgumentException e) {
            alerta("Error de sintaxis", e.getMessage());
            log("ERROR", "Error de sintaxis: " + e.getMessage());
            setEstado("ERROR SINTAXIS", "lbl-estado-error");
            mostrarConsolaEnTabla();
            return;
        }

        // Ejecutar pandas ────────────────────────────────────────
        setEstado("EJECUTANDO...", "lbl-estado-running");
        log("INFO", "Ejecutando script con pandas...");
        PandasRunner.Resultado resultado = PandasRunner.ejecutar(ultimoScriptPy, 30);

        if (!resultado.exitoso()) {
            alerta("Error en Python", resultado.error());
            log("ERROR", resultado.error());
            setEstado("ERROR PYTHON", "lbl-estado-error");
            mostrarConsolaEnTabla();
            return;
        }

        log("INFO", "Consulta ejecutada con éxito.");

        // Mostrar resultado ──────────────────────────────────────
        String salida = resultado.salida();
        if (!salida.contains(",") && !salida.contains("\n")) {
            mostrarEscalar(salida);
        } else {
            mostrarResultadoCSV(salida);
        }

        setEstado("LISTO", "lbl-estado");
        activarTab(tabResultado, tabTokens, tabConsola);
    }

    // ══════════════════════════════════════════════════════════
    //  TABS
    // ══════════════════════════════════════════════════════════

    @FXML public void onTabResultado() { activarTab(tabResultado, tabTokens, tabConsola); }
    @FXML public void onTabTokens()    { activarTab(tabTokens, tabResultado, tabConsola);  mostrarTokensEnTabla(ultimosTokens); }
    @FXML public void onTabConsola()   { activarTab(tabConsola, tabResultado, tabTokens);  mostrarConsolaEnTabla(); }

    private void activarTab(Button activo, Button... inactivos) {
        activo.getStyleClass().remove("btn-tab");
        if (!activo.getStyleClass().contains("btn-tab-active"))
            activo.getStyleClass().add("btn-tab-active");
        for (Button b : inactivos) {
            b.getStyleClass().remove("btn-tab-active");
            if (!b.getStyleClass().contains("btn-tab"))
                b.getStyleClass().add("btn-tab");
        }
    }

    // ══════════════════════════════════════════════════════════
    //  BUILDERS DE TABLA
    // ══════════════════════════════════════════════════════════

    private void mostrarResultadoCSV(String csvTexto) {
        tablaResultados.getColumns().clear();
        tablaResultados.getItems().clear();

        String[] lineas = csvTexto.split("\n");
        if (lineas.length == 0) return;

        String[] headers = lineas[0].split(",");
        for (int i = 0; i < headers.length; i++) {
            final int ci = i;
            TableColumn<ObservableList<String>, String> col =
                    new TableColumn<>(headers[i].trim().toUpperCase());
            col.setCellValueFactory(data -> {
                var row = data.getValue();
                return new SimpleStringProperty(ci < row.size() ? row.get(ci) : "");
            });
            col.setCellFactory(column -> new TableCell<>() {
                @Override protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    getStyleClass().removeAll("cell-id", "cell-email");
                    if (empty || item == null) { setText(null); return; }
                    setText(item);
                    if (item.contains("@"))            getStyleClass().add("cell-email");
                    else if (item.matches("^\\d{3}$")) getStyleClass().add("cell-id");
                }
            });
            col.setPrefWidth(150);
            tablaResultados.getColumns().add(col);
        }

        ObservableList<ObservableList<String>> items = FXCollections.observableArrayList();
        for (int r = 1; r < lineas.length; r++) {
            if (lineas[r].trim().isEmpty()) continue;
            ObservableList<String> row = FXCollections.observableArrayList();
            for (String c : lineas[r].split(",", -1)) row.add(c.trim());
            items.add(row);
        }
        tablaResultados.setItems(items);
        lblFilas.setText(fmt(items.size()));
        log("INFO", "Filas mostradas: " + items.size());
    }

    private void mostrarEscalar(String valor) {
        tablaResultados.getColumns().clear();
        tablaResultados.getItems().clear();
        TableColumn<ObservableList<String>, String> col = new TableColumn<>("RESULTADO");
        col.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get(0)));
        col.setPrefWidth(300);
        tablaResultados.getColumns().add(col);
        tablaResultados.setItems(FXCollections.observableArrayList(
                Collections.singleton(FXCollections.observableArrayList(valor))
        ));
        lblFilas.setText("1");
    }

    private void mostrarTokensEnTabla(List<Token> tokens) {
        tablaResultados.getColumns().clear();
        tablaResultados.getItems().clear();

        if (tokens == null || tokens.isEmpty()) {
            log("WARN", "No hay tokens. Ejecuta una consulta primero.");
            return;
        }

        TableColumn<ObservableList<String>, String> cId     = new TableColumn<>("ID");
        TableColumn<ObservableList<String>, String> cNombre = new TableColumn<>("TOKEN");
        TableColumn<ObservableList<String>, String> cLexema = new TableColumn<>("LEXEMA");

        cId    .setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get(0)));
        cNombre.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get(1)));
        cLexema.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get(2)));

        cId.setPrefWidth(70); cNombre.setPrefWidth(220); cLexema.setPrefWidth(180);
        tablaResultados.getColumns().addAll(cId, cNombre, cLexema);

        ObservableList<ObservableList<String>> items = FXCollections.observableArrayList();
        for (Token t : tokens)
            items.add(FXCollections.observableArrayList(
                    String.valueOf(t.getId()), t.getNombre(), t.getLexema()
            ));
        tablaResultados.setItems(items);
    }

    private void mostrarConsolaEnTabla() {
        tablaResultados.getColumns().clear();
        tablaResultados.getItems().clear();

        TableColumn<ObservableList<String>, String> cNivel = new TableColumn<>("NIVEL");
        TableColumn<ObservableList<String>, String> cMsg   = new TableColumn<>("MENSAJE");
        cNivel.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get(0)));
        cMsg  .setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get(1)));
        cNivel.setPrefWidth(80); cMsg.setPrefWidth(580);
        tablaResultados.getColumns().addAll(cNivel, cMsg);

        ObservableList<ObservableList<String>> items = FXCollections.observableArrayList();
        for (String entrada : logConsola) {
            String[] p = entrada.split("\\|", 2);
            items.add(FXCollections.observableArrayList(
                    p.length > 1 ? p[0] : "INFO",
                    p.length > 1 ? p[1] : entrada
            ));
        }

        // Mostrar script generado
        if (!ultimoScriptPy.isEmpty()) {
            items.add(FXCollections.observableArrayList("", ""));
            items.add(FXCollections.observableArrayList("SCRIPT", "── Script Python generado ──"));
            for (String linea : ultimoScriptPy.split("\n"))
                items.add(FXCollections.observableArrayList("PY", linea));
        }

        tablaResultados.setItems(items);
    }

    // ══════════════════════════════════════════════════════════
    //  ENCABEZADOS
    // ══════════════════════════════════════════════════════════

    private void cargarEncabezados(File file) {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String primera = br.readLine();
            if (primera != null) {
                String[] cols = primera.split(",");
                lblColumnas.setText(String.valueOf(cols.length));
                long filas = br.lines().count();
                lblFilas.setText(fmt(filas));
                log("INFO", "Columnas: " + cols.length + " | Filas: " + filas);
            }
        } catch (IOException e) {
            log("ERROR", "No se pudieron leer encabezados: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════
    //  LÍNEAS Y CURSOR
    // ══════════════════════════════════════════════════════════

    private void sincronizarNumerosLinea() {
        if (lineNumbers == null) return;
        editorSQL.textProperty().addListener((obs, o, n) -> {
            long count = n.chars().filter(c -> c == '\n').count() + 1;
            actualizarNumerosLinea((int) count);
        });
    }

    private void actualizarNumerosLinea(int count) {
        if (lineNumbers == null) return;
        lineNumbers.getChildren().clear();
        for (int i = 1; i <= count; i++) {
            Label lbl = new Label(String.valueOf(i));
            lbl.getStyleClass().add("line-number");
            lineNumbers.getChildren().add(lbl);
        }
    }

    private void sincronizarCursor() {
        editorSQL.caretPositionProperty().addListener((obs, o, n) -> {
            String texto = editorSQL.getText();
            int caret = n.intValue(), linea = 1, col = 1;
            for (int i = 0; i < Math.min(caret, texto.length()); i++) {
                if (texto.charAt(i) == '\n') { linea++; col = 1; } else col++;
            }
            lblCursor.setText("LÍNEA " + linea + ", COL " + col);
        });
    }

    // ══════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════

    private void setEstado(String texto, String cssClass) {
        lblEstado.getStyleClass().removeAll("lbl-estado","lbl-estado-error","lbl-estado-running");
        lblEstado.getStyleClass().add(cssClass);
        lblEstado.setText("● " + texto);
    }

    private void log(String nivel, String mensaje) {
        logConsola.add(nivel + "|" + mensaje);
        System.out.println("[" + nivel + "] " + mensaje);
    }

    private String fmt(long n)      { return new DecimalFormat("#,###").format(n); }
    private String fmtBytes(long b) {
        if (b < 1024)    return b + " B";
        if (b < 1048576) return String.format("%.1f KB", b / 1024.0);
        return String.format("%.1f MB", b / 1048576.0);
    }

    private void alerta(String titulo, String msg) {
        Alert a = new Alert(Alert.AlertType.WARNING);
        a.setTitle(titulo); a.setHeaderText(null); a.setContentText(msg);
        a.showAndWait();
    }
}