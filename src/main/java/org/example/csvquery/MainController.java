package org.example.csvquery;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;

public class MainController implements Initializable {

    // ── Editor ──────────────────────────────────────────────
    @FXML private TextArea editorSQL;

    // ── Status bar ──────────────────────────────────────────
    @FXML private Label lblEstado;
    @FXML private Label lblArchivo;
    @FXML private Label lblColumnas;
    @FXML private Label lblEncoding;
    @FXML private Label lblCursor;

    // ── Result tabs ─────────────────────────────────────────
    @FXML private Button tabResultado;
    @FXML private Button tabTokens;
    @FXML private Button tabConsola;

    // ── Results table ────────────────────────────────────────
    @FXML private TableView<ObservableList<String>> tablaResultados;
    @FXML private TableColumn<ObservableList<String>, String> colId;
    @FXML private TableColumn<ObservableList<String>, String> colNombre;
    @FXML private TableColumn<ObservableList<String>, String> colEdad;
    @FXML private TableColumn<ObservableList<String>, String> colEmail;
    @FXML private TableColumn<ObservableList<String>, String> colUbicacion;

    // ── Dictionary panel ─────────────────────────────────────
    @FXML private Label lblFilas;
    @FXML private Label lblPeso;

    // ── Internal state ───────────────────────────────────────
    private File archivoCSV;
    private List<String[]> csvData = new ArrayList<>();   // all rows (header at index 0)
    private String[] csvHeaders = new String[0];
    private boolean  temaClaro  = false;

    // ── Root (para cambio de tema) ───────────────────────────
    @FXML private BorderPane rootPane;

    // ── Topbar ──────────────────────────────────────────────
    @FXML private Button btnTema;

    private static final String ESTILO_TAB_ACTIVO =
            "-fx-background-color: transparent;" +
            "-fx-text-fill: #00ff88;" +
            "-fx-font-family: 'Consolas';" +
            "-fx-font-size: 11px;" +
            "-fx-font-weight: bold;" +
            "-fx-border-color: transparent transparent #00ff88 transparent;" +
            "-fx-border-width: 0 0 2 0;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 0 20 0 20;" +
            "-fx-min-height: 40;";

    private static final String ESTILO_TAB_INACTIVO =
            "-fx-background-color: transparent;" +
            "-fx-text-fill: #555555;" +
            "-fx-font-family: 'Consolas';" +
            "-fx-font-size: 11px;" +
            "-fx-border-width: 0;" +
            "-fx-cursor: hand;" +
            "-fx-padding: 0 20 0 20;" +
            "-fx-min-height: 40;";

    // ────────────────────────────────────────────────────────
    @Override
    public void initialize(URL location, ResourceBundle resources) {
        editorSQL.setText(
            "-- Ejecutando consulta local\n\n" +
            "TRAER nombre DESDE \"datos.csv\" DONDE edad > 20;"
        );

        configurarTablaVacia();
        sincronizarCursor();
        setEstado("LISTO", "#00ff88");
    }

    // ══════════════════════════════════════════════════════════
    //  TOPBAR ACTIONS
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
            cargarCSV(file);
        }
    }

    @FXML
    public void onLimpiarConsola() {
        editorSQL.clear();
        tablaResultados.getItems().clear();
        tablaResultados.getColumns().clear();
        setEstado("LISTO", "#00ff88");
        lblCursor.setText("LÍNEA 1, COL 1");
    }

    @FXML
    public void onEjecutarConsulta() {
        String sql = editorSQL.getText().trim();

        if (sql.isEmpty()) {
            mostrarAlerta("Consulta vacía", "Escribe una consulta antes de ejecutar.");
            return;
        }

        if (archivoCSV == null) {
            mostrarAlerta("Sin archivo", "Abre un archivo CSV primero.");
            return;
        }

        setEstado("EJECUTANDO...", "#ffaa00");
        ejecutarConsulta(sql);
        setEstado("LISTO", "#00ff88");
    }

    // ══════════════════════════════════════════════════════════
    //  TAB ACTIONS
    // ══════════════════════════════════════════════════════════

    @FXML
    public void onTabResultado() {
        tabResultado.setStyle(ESTILO_TAB_ACTIVO);
        tabTokens.setStyle(ESTILO_TAB_INACTIVO);
        tabConsola.setStyle(ESTILO_TAB_INACTIVO);
        // Show results table (already visible by default)
    }

    @FXML
    public void onTabTokens() {
        tabResultado.setStyle(ESTILO_TAB_INACTIVO);
        tabTokens.setStyle(ESTILO_TAB_ACTIVO);
        tabConsola.setStyle(ESTILO_TAB_INACTIVO);

        String sql = editorSQL.getText();
        List<Token> tokens = tokenizar(sql);
        mostrarTokensEnTabla(tokens);
    }

    @FXML
    public void onTabConsola() {
        tabResultado.setStyle(ESTILO_TAB_INACTIVO);
        tabTokens.setStyle(ESTILO_TAB_INACTIVO);
        tabConsola.setStyle(ESTILO_TAB_ACTIVO);

        mostrarConsolaEnTabla();
    }

    // ══════════════════════════════════════════════════════════
    //  CSV LOADING
    // ══════════════════════════════════════════════════════════

    private void cargarCSV(File file) {
        csvData.clear();

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {

            String line;
            while ((line = br.readLine()) != null) {
                csvData.add(parsearLineaCSV(line));
            }

            if (csvData.isEmpty()) {
                mostrarAlerta("Archivo vacío", "El CSV no contiene datos.");
                return;
            }

            archivoCSV = file;
            csvHeaders = csvData.get(0);

            // Update status bar
            lblArchivo.setText(file.getName().toUpperCase());
            lblColumnas.setText(String.valueOf(csvHeaders.length));
            lblFilas.setText(formatearNumero(csvData.size() - 1));
            lblPeso.setText(formatearPeso(file.length()));

            // Pre-build table with all data
            construirTabla(csvHeaders, csvData.subList(1, csvData.size()));
            setEstado("LISTO", "#00ff88");

            // Auto-suggest query
            editorSQL.setText(
                "-- Ejecutando consulta local\n\n" +
                "TRAER * DESDE \"" + file.getName() + "\";"
            );

        } catch (IOException e) {
            mostrarAlerta("Error al leer CSV", e.getMessage());
            setEstado("ERROR", "#ff4444");
        }
    }

    // ══════════════════════════════════════════════════════════
    //  QUERY EXECUTION  (custom mini-language: TRAER ... DESDE ... DONDE ...)
    // ══════════════════════════════════════════════════════════

    private void ejecutarConsulta(String sql) {
        // Strip comments
        String query = sql.replaceAll("--[^\n]*", "").trim();

        try {
            ConsultaParseada parsed = parsearConsulta(query);
            List<String[]> resultados = filtrarDatos(parsed);
            construirTabla(parsed.columnas, resultados);
            lblFilas.setText(formatearNumero(resultados.size()));
        } catch (Exception e) {
            mostrarAlerta("Error de consulta", e.getMessage());
            setEstado("ERROR", "#ff4444");
        }
    }

    private ConsultaParseada parsearConsulta(String query) throws Exception {
        // Normalize
        String q = query.trim().replaceAll(";$", "").trim();
        String upper = q.toUpperCase();

        if (!upper.startsWith("TRAER")) {
            throw new Exception("La consulta debe comenzar con TRAER.");
        }

        // Find positions
        int desdeIdx = upper.indexOf(" DESDE ");
        int dondeIdx = upper.indexOf(" DONDE ");

        if (desdeIdx < 0) throw new Exception("Falta la cláusula DESDE.");

        // Columns
        String colsPart = q.substring(5, desdeIdx).trim();
        String[] columnas;
        if (colsPart.equals("*")) {
            columnas = csvHeaders;
        } else {
            columnas = Arrays.stream(colsPart.split(","))
                             .map(String::trim)
                             .toArray(String[]::new);
        }

        // File (ignored for now — we use the loaded file)
        // int endOfDesde = (dondeIdx > 0) ? dondeIdx : q.length();

        // WHERE condition
        String condicion = null;
        if (dondeIdx > 0) {
            condicion = q.substring(dondeIdx + 7).trim();
        }

        return new ConsultaParseada(columnas, condicion);
    }

    private List<String[]> filtrarDatos(ConsultaParseada parsed) throws Exception {
        List<String[]> resultado = new ArrayList<>();

        // Resolve column indices
        int[] indices = new int[parsed.columnas.length];
        for (int i = 0; i < parsed.columnas.length; i++) {
            indices[i] = indiceDeColumna(parsed.columnas[i]);
            if (indices[i] < 0) {
                throw new Exception("Columna no encontrada: " + parsed.columnas[i]);
            }
        }

        for (int r = 1; r < csvData.size(); r++) {
            String[] fila = csvData.get(r);

            if (parsed.condicion != null && !evaluarCondicion(fila, parsed.condicion)) {
                continue;
            }

            String[] filaFiltrada = new String[indices.length];
            for (int i = 0; i < indices.length; i++) {
                filaFiltrada[i] = indices[i] < fila.length ? fila[indices[i]] : "";
            }
            resultado.add(filaFiltrada);
        }

        return resultado;
    }

    private boolean evaluarCondicion(String[] fila, String condicion) {
        try {
            // Supports: columna > valor, columna < valor, columna = "valor", columna != "valor"
            String[] ops = {"!=", ">=", "<=", ">", "<", "="};
            for (String op : ops) {
                int idx = condicion.indexOf(op);
                if (idx < 0) continue;

                String colName  = condicion.substring(0, idx).trim();
                String valorStr = condicion.substring(idx + op.length()).trim()
                                           .replaceAll("\"", "").replaceAll("'", "");

                int colIdx = indiceDeColumna(colName);
                if (colIdx < 0) return true; // unknown column → pass through

                String cellValue = colIdx < fila.length ? fila[colIdx].trim() : "";

                // Numeric comparison?
                try {
                    double cellNum  = Double.parseDouble(cellValue);
                    double valorNum = Double.parseDouble(valorStr);
                    return switch (op) {
                        case ">"  -> cellNum >  valorNum;
                        case "<"  -> cellNum <  valorNum;
                        case ">=" -> cellNum >= valorNum;
                        case "<=" -> cellNum <= valorNum;
                        case "="  -> cellNum == valorNum;
                        case "!=" -> cellNum != valorNum;
                        default   -> true;
                    };
                } catch (NumberFormatException e) {
                    // String comparison
                    int cmp = cellValue.compareToIgnoreCase(valorStr);
                    return switch (op) {
                        case "="  -> cmp == 0;
                        case "!=" -> cmp != 0;
                        case ">"  -> cmp >  0;
                        case "<"  -> cmp <  0;
                        case ">=" -> cmp >= 0;
                        case "<=" -> cmp <= 0;
                        default   -> true;
                    };
                }
            }
        } catch (Exception ignored) {}
        return true;
    }

    // ══════════════════════════════════════════════════════════
    //  TABLE BUILDERS
    // ══════════════════════════════════════════════════════════

    private void construirTabla(String[] headers, List<String[]> filas) {
        tablaResultados.getColumns().clear();
        tablaResultados.getItems().clear();

        for (int i = 0; i < headers.length; i++) {
            final int colIndex = i;
            TableColumn<ObservableList<String>, String> col =
                new TableColumn<>(headers[i].toUpperCase());

            col.setCellValueFactory(data -> {
                ObservableList<String> row = data.getValue();
                return new SimpleStringProperty(
                    colIndex < row.size() ? row.get(colIndex) : ""
                );
            });

            // Style email-like values in green
            col.setCellFactory(column -> new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setStyle("");
                    } else {
                        setText(item);
                        if (item.contains("@")) {
                            setStyle("-fx-text-fill: #00aa66; -fx-font-family: 'Consolas'; -fx-font-size: 12px;");
                        } else if (item.matches("^\\d{3}$")) {
                            setStyle("-fx-text-fill: #00ff88; -fx-font-family: 'Consolas'; -fx-font-size: 12px;");
                        } else {
                            setStyle("-fx-text-fill: #cccccc; -fx-font-family: 'Consolas'; -fx-font-size: 12px;");
                        }
                    }
                }
            });

            col.setPrefWidth(160);
            tablaResultados.getColumns().add(col);
        }

        ObservableList<ObservableList<String>> items = FXCollections.observableArrayList();
        for (String[] fila : filas) {
            ObservableList<String> row = FXCollections.observableArrayList(Arrays.asList(fila));
            items.add(row);
        }
        tablaResultados.setItems(items);
    }

    private void configurarTablaVacia() {
        tablaResultados.getColumns().clear();
        tablaResultados.getItems().clear();
    }

    private void mostrarTokensEnTabla(List<Token> tokens) {
        tablaResultados.getColumns().clear();
        tablaResultados.getItems().clear();

        TableColumn<ObservableList<String>, String> colTipo   = new TableColumn<>("TIPO");
        TableColumn<ObservableList<String>, String> colValor  = new TableColumn<>("VALOR");
        TableColumn<ObservableList<String>, String> colPosicion = new TableColumn<>("POSICIÓN");

        colTipo.setCellValueFactory(d  -> new SimpleStringProperty(d.getValue().get(0)));
        colValor.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get(1)));
        colPosicion.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get(2)));

        colTipo.setPrefWidth(140);
        colValor.setPrefWidth(200);
        colPosicion.setPrefWidth(100);

        tablaResultados.getColumns().addAll(colTipo, colValor, colPosicion);

        ObservableList<ObservableList<String>> items = FXCollections.observableArrayList();
        for (Token t : tokens) {
            items.add(FXCollections.observableArrayList(t.tipo, t.valor, String.valueOf(t.posicion)));
        }
        tablaResultados.setItems(items);
    }

    private void mostrarConsolaEnTabla() {
        tablaResultados.getColumns().clear();
        tablaResultados.getItems().clear();

        TableColumn<ObservableList<String>, String> colNivel    = new TableColumn<>("NIVEL");
        TableColumn<ObservableList<String>, String> colMensaje  = new TableColumn<>("MENSAJE");

        colNivel.setCellValueFactory(d   -> new SimpleStringProperty(d.getValue().get(0)));
        colMensaje.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().get(1)));

        colNivel.setPrefWidth(100);
        colMensaje.setPrefWidth(500);

        tablaResultados.getColumns().addAll(colNivel, colMensaje);

        ObservableList<ObservableList<String>> items = FXCollections.observableArrayList();

        if (archivoCSV != null) {
            items.add(FXCollections.observableArrayList("INFO",
                "Archivo cargado: " + archivoCSV.getAbsolutePath()));
            items.add(FXCollections.observableArrayList("INFO",
                "Columnas detectadas: " + csvHeaders.length));
            items.add(FXCollections.observableArrayList("INFO",
                "Filas de datos: " + (csvData.size() - 1)));
        } else {
            items.add(FXCollections.observableArrayList("WARN", "No se ha cargado ningún archivo CSV."));
        }

        items.add(FXCollections.observableArrayList("INFO",
            "Última consulta: " + editorSQL.getText().replaceAll("\n", " ").trim()));

        tablaResultados.setItems(items);
    }

    // ══════════════════════════════════════════════════════════
    //  LEXER  (simple tokenizer for the custom SQL dialect)
    // ══════════════════════════════════════════════════════════

    private List<Token> tokenizar(String sql) {
        List<Token> tokens = new ArrayList<>();
        Set<String> palabrasReservadas = new HashSet<>(Arrays.asList(
            "TRAER", "DESDE", "DONDE", "Y", "O", "NO",
            "ORDENAR", "POR", "LIMITAR", "AGRUPAR"
        ));

        // Remove comments
        String limpio = sql.replaceAll("--[^\n]*", "").trim();
        String[] partes = limpio.split("(?<=\\s)|(?=\\s)|(?<=[,;\"*=<>!])|(?=[,;\"*=<>!])");

        int pos = 0;
        for (String parte : partes) {
            String p = parte.trim();
            if (p.isEmpty()) { pos += parte.length(); continue; }

            String tipo;
            if (palabrasReservadas.contains(p.toUpperCase())) {
                tipo = "PALABRA_RESERVADA";
            } else if (p.matches("-?\\d+(\\.\\d+)?")) {
                tipo = "NUMERO";
            } else if (p.startsWith("\"") || p.startsWith("'")) {
                tipo = "CADENA";
            } else if (p.matches("[=<>!]+")) {
                tipo = "OPERADOR";
            } else if (p.equals(",")) {
                tipo = "COMA";
            } else if (p.equals(";")) {
                tipo = "FIN_SENTENCIA";
            } else if (p.equals("*")) {
                tipo = "ASTERISCO";
            } else {
                tipo = "IDENTIFICADOR";
            }

            tokens.add(new Token(tipo, p, pos));
            pos += parte.length();
        }

        return tokens;
    }

    // ══════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════

    private int indiceDeColumna(String nombre) {
        for (int i = 0; i < csvHeaders.length; i++) {
            if (csvHeaders[i].trim().equalsIgnoreCase(nombre.trim())) return i;
        }
        return -1;
    }

    private String[] parsearLineaCSV(String linea) {
        List<String> campos = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean enComillas = false;

        for (char c : linea.toCharArray()) {
            if (c == '"') {
                enComillas = !enComillas;
            } else if (c == ',' && !enComillas) {
                campos.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        campos.add(sb.toString().trim());
        return campos.toArray(new String[0]);
    }

    private String formatearNumero(long n) {
        return new DecimalFormat("#,###").format(n);
    }

    private String formatearPeso(long bytes) {
        if (bytes < 1024)        return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private void setEstado(String texto, String color) {
        lblEstado.setText("● " + texto);
        lblEstado.setStyle(
            "-fx-text-fill: " + color + ";" +
            "-fx-font-family: 'Consolas';" +
            "-fx-font-size: 10px;"
        );
    }

    private void sincronizarCursor() {
        editorSQL.caretPositionProperty().addListener((obs, oldV, newV) -> {
            String texto = editorSQL.getText();
            int caret = newV.intValue();
            int linea = 1, col = 1;
            for (int i = 0; i < Math.min(caret, texto.length()); i++) {
                if (texto.charAt(i) == '\n') { linea++; col = 1; }
                else col++;
            }
            lblCursor.setText("LÍNEA " + linea + ", COL " + col);
        });
    }

    private void mostrarAlerta(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

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
    //  INNER CLASSES
    // ══════════════════════════════════════════════════════════

    private static class ConsultaParseada {
        final String[] columnas;
        final String   condicion;
        ConsultaParseada(String[] columnas, String condicion) {
            this.columnas  = columnas;
            this.condicion = condicion;
        }
    }

    private static class Token {
        final String tipo;
        final String valor;
        final int    posicion;
        Token(String tipo, String valor, int posicion) {
            this.tipo     = tipo;
            this.valor    = valor;
            this.posicion = posicion;
        }
    }
}
