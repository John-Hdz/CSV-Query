package org.example.csvquery;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import org.example.csvquery.models.Lexer;
import org.example.csvquery.Parser;
import org.example.csvquery.models.Token;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.io.*;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MainController implements Initializable {

    // ── Root ─────────────────────────────────────────────────
    @FXML private BorderPane rootPane;

    // ── Topbar ───────────────────────────────────────────────
    @FXML private Button btnTema;

    // ── Editor ───────────────────────────────────────────────
//    @FXML private TextArea editorSQL;
    @FXML private StackPane editorContainer;
    private CodeArea editorSQL;
    @FXML private VBox     lineNumbers;

    // ── Status bar ───────────────────────────────────────────
    @FXML private Label lblEstado;
    @FXML private Label lblArchivo;
    @FXML private Label lblColumnas;
    @FXML private Label lblEncoding;
    @FXML private Label lblCursor;

    // ── Result tabs ──────────────────────────────────────────
    @FXML private Button tabResultado;
    @FXML private Button tabLexico;
    @FXML private Button tabTokens;
    @FXML private Button tabConsola;
    @FXML private Button tabCodigo;

    // ── Results table ────────────────────────────────────────
    @FXML private TableView<ObservableList<String>> tablaResultados;

    // ── Panel léxico (ScrollPane + VBox internos del FXML) ───
    @FXML private ScrollPane panelLexico;
    @FXML private VBox       vboxLexico;

    // ── Panel código intermedio ───────────────────────────────
    @FXML private VBox     panelCodigo;
    @FXML private TextArea areaCodigo;

    // ── Dictionary panel ─────────────────────────────────────
    @FXML private Label lblFilas;
    @FXML private Label lblPeso;

    // ── Estado interno ───────────────────────────────────────
    private File         archivoCSV;
    private boolean      temaClaro      = false;
    private List<Token>  ultimosTokens  = new ArrayList<>();
    private List<Token>  ultimosErrores = new ArrayList<>();
    private List<String> logConsola     = new ArrayList<>();
    private String       ultimoScriptPy = "";
    private org.example.csvquery.models.ast.NodoConsulta ultimoAST = null;

    private static final String RUTA_AUTOMATA =
            "src/main/resources/CSV/Matriz de transicion 2.csv";

    // ══════════════════════════════════════════════════════════
    //  INICIALIZACIÓN
    // ══════════════════════════════════════════════════════════

    @Override
    public void initialize(URL location, ResourceBundle resources) {

        editorSQL = new CodeArea();
        editorSQL.getStyleClass().add("mi-editor-codigo");
        editorSQL.setParagraphGraphicFactory(LineNumberFactory.get(editorSQL));

        editorSQL.textProperty().addListener((obs, oldText, newText) -> {
            editorSQL.setStyleSpans(0, computeHighlighting(newText));
        });

        editorContainer.getChildren().add(editorSQL);

        editorSQL.replaceText(
                "-- Escribe tu consulta aquí\n\n" +
                        "TRAER nombre, edad DESDE \"src/main/resources/CSV/datos.csv\" DONDE edad > 20;"
        );

        sincronizarCursor();

        setEstado("LISTO", "lbl-estado");
        log("INFO", "Aplicación iniciada.");
        log("INFO", "Autómata esperado en: " + RUTA_AUTOMATA);
    }
    //Resalto de letras

    // 1. Definimos las palabras reservadas y funciones
    private static final String[] KEYWORDS = new String[] {
            "TRAER", "DESDE", "DONDE", "Y", "O", "ORDENAR", "POR", "ASC", "DESC", "LIMITAR", "DISTINTO", "GUARDAR", "EN"
    };
    private static final String[] FUNCTIONS = new String[] {
            "CONTAR", "SUMA", "PROMEDIO", "MAXIMO", "MINIMO"
    };

    // 2. Creamos la expresión regular (Regex) para detectarlas
    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    private static final String FUNCTION_PATTERN = "\\b(" + String.join("|", FUNCTIONS) + ")\\b";
    private static final String STRING_PATTERN = "\"([^\"]*)\""; // Para detectar textos entre comillas

    private static final Pattern PATTERN = Pattern.compile(
            "(?<KEYWORD>" + KEYWORD_PATTERN + ")"
                    + "|(?<FUNCTION>" + FUNCTION_PATTERN + ")"
                    + "|(?<STRING>" + STRING_PATTERN + ")"
    );
    //El motor que asigna las clases CSS según lo que encuentre
    private StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();

        while(matcher.find()) {
            String styleClass =
                    matcher.group("KEYWORD") != null ? "keyword" :
                            matcher.group("FUNCTION") != null ? "function" :
                                    matcher.group("STRING") != null ? "string" :
                                            "default-text"; // <-- CAMBIO AQUÍ: Clase por defecto en lugar de null

            // CAMBIO AQUÍ: Usamos "default-text" en lugar de emptyList()
            spansBuilder.add(Collections.singleton("default-text"), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        // CAMBIO AQUÍ: Usamos "default-text" para el texto final
        spansBuilder.add(Collections.singleton("default-text"), text.length() - lastKwEnd);
        return spansBuilder.create();
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
            String ruta = file.getAbsolutePath().replace("\\", "/");
            editorSQL.replaceText(
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
        ultimosErrores.clear();
        ultimoAST = null;
        logConsola.clear();
        ultimoScriptPy = "";
        if (vboxLexico != null) vboxLexico.getChildren().clear();
        setEstado("LISTO", "lbl-estado");
        lblCursor.setText("LÍNEA 1, COL 1");
        actualizarNumerosLinea(1);
        mostrarPanel(tablaResultados);
        activarTab(tabResultado, tabLexico, tabTokens, tabConsola);
    }

    // ══════════════════════════════════════════════════════════
    //  EJECUTAR CONSULTA — flujo principal
    // ══════════════════════════════════════════════════════════

    @FXML
    public void onEjecutarConsulta() {
        String texto = editorSQL.getText().trim();

        if (texto.isEmpty()) {
            alerta("Consulta vacía", "Escribe una consulta antes de ejecutar.");
            return;
        }

        setEstado("ANALIZANDO...", "lbl-estado-running");
        log("INFO", "Iniciando análisis léxico...");

        // ── Archivo temporal para el Lexer ───────────────────────────────
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

        // ── Análisis Léxico ───────────────────────────────────────────────
        Lexer lexer = new Lexer(RUTA_AUTOMATA);
        lexer.analizarArchivo(tempQuery.getAbsolutePath());
        tempQuery.delete();

        ultimosTokens  = lexer.getTablaSimbolos();
        ultimosErrores = lexer.getPilaErrores();

        log("INFO", "Tokens reconocidos: " + ultimosTokens.size());

        // Construir el panel léxico siempre, incluso si hay errores
        construirPanelSintactico(ultimoAST, ultimosTokens, ultimosErrores);

        if (!ultimosErrores.isEmpty()) {
            log("ERROR", "Errores léxicos detectados: " + ultimosErrores.size());
            for (Token e : ultimosErrores)
                log("ERROR", "  " + e.getNombre() + ": '" + e.getLexema() + "'");
            alerta("Error léxico",
                    "Se encontraron " + ultimosErrores.size() + " error(es) léxico(s).\n" +
                            "Revisa la pestaña '🔬 Análisis Léxico'.");
            setEstado("ERROR LÉXICO", "lbl-estado-error");
            // Navegar automáticamente al panel léxico para mostrar el error
            mostrarPanel(panelLexico);
            activarTab(tabLexico, tabResultado, tabTokens, tabConsola);
            return;
        }

        // ── Análisis Sintáctico ───────────────────────────────────────────
        setEstado("ANÁLISIS SINTÁCTICO...", "lbl-estado-running");
        log("INFO", "Iniciando análisis sintáctico (Parser)...");
        org.example.csvquery.models.ast.NodoConsulta ast;
        try {
            Parser parser = new Parser(ultimosTokens);
            ast = parser.parsear();
            ultimoAST = ast;
            log("INFO", "AST construido correctamente.");
        } catch (Parser.ParseException e) {
            alerta("Error Sintáctico", e.getMessage());
            log("ERROR", "Error sintáctico: " + e.getMessage());
            setEstado("ERROR SINTÁCTICO", "lbl-estado-error");
            mostrarConsolaEnTabla();
            mostrarPanel(tablaResultados);
            activarTab(tabConsola, tabLexico, tabResultado, tabTokens);
            return;
        }

        // ── Análisis Semántico ────────────────────────────────────────────
        setEstado("ANÁLISIS SEMÁNTICO...", "lbl-estado-running");
        log("INFO", "Iniciando análisis semántico...");
        try {
            ast.validarSemantica();
            log("INFO", "Validación semántica exitosa.");
        } catch (Exception e) {
            alerta("Error Semántico", e.getMessage());
            log("ERROR", "Error semántico: " + e.getMessage());
            setEstado("ERROR SEMÁNTICO", "lbl-estado-error");
            mostrarConsolaEnTabla();
            mostrarPanel(tablaResultados);
            activarTab(tabConsola, tabLexico, tabResultado, tabTokens);
            return;
        }

        // ── Generación de código Python ───────────────────────────────────
        setEstado("GENERANDO SCRIPT...", "lbl-estado-running");
        ultimoScriptPy = ast.generarPython();
        log("INFO", "Script Python generado:");
        for (String linea : ultimoScriptPy.split("\n"))
            log("SCRIPT", linea);

        // ── Ejecución con Pandas ──────────────────────────────────────────
        setEstado("EJECUTANDO...", "lbl-estado-running");
        log("INFO", "Ejecutando script con pandas...");
        PandasRunner.Resultado resultado = PandasRunner.ejecutar(ultimoScriptPy, 30);

        if (!resultado.exitoso()) {
            alerta("Error en Python", resultado.error());
            log("ERROR", resultado.error());
            setEstado("ERROR PYTHON", "lbl-estado-error");
            mostrarConsolaEnTabla();
            mostrarPanel(tablaResultados);
            activarTab(tabConsola, tabLexico, tabResultado, tabTokens);
            return;
        }

        log("INFO", "Consulta ejecutada con éxito.");

        // ── Mostrar resultado ─────────────────────────────────────────────
        String salida = resultado.salida();
        if (!salida.contains(",") && !salida.contains("\n")) {
            mostrarEscalar(salida);
        } else {
            mostrarResultadoCSV(salida);
        }

        setEstado("LISTO", "lbl-estado");
        mostrarPanel(tablaResultados);
        activarTab(tabResultado, tabLexico, tabTokens, tabConsola);
    }

    // ══════════════════════════════════════════════════════════
    //  TABS
    // ══════════════════════════════════════════════════════════

    @FXML public void onTabResultado() {
        mostrarPanel(tablaResultados);
        activarTab(tabResultado, tabLexico, tabTokens, tabConsola, tabCodigo);
    }

    @FXML public void onTabLexico() {
        mostrarPanel(panelLexico);
        activarTab(tabLexico, tabResultado, tabTokens, tabConsola, tabCodigo);
        construirPanelSintactico(ultimoAST, ultimosTokens, ultimosErrores);
    }

    @FXML public void onTabTokens() {
        mostrarPanel(tablaResultados);
        activarTab(tabTokens, tabLexico, tabResultado, tabConsola, tabCodigo);
        mostrarTokensEnTabla(ultimosTokens);
    }

    @FXML public void onTabConsola() {
        mostrarPanel(tablaResultados);
        activarTab(tabConsola, tabLexico, tabResultado, tabTokens, tabCodigo);
        mostrarConsolaEnTabla();
    }

    @FXML public void onTabCodigo() {
        mostrarPanel(panelCodigo);
        activarTab(tabCodigo, tabLexico, tabResultado, tabTokens, tabConsola);
        if (ultimoScriptPy == null || ultimoScriptPy.isBlank()) {
            areaCodigo.setText("-- Ejecuta una consulta para ver el código Python generado.");
        } else {
            areaCodigo.setText(ultimoScriptPy);
        }
    }

    /** Alterna visibilidad entre los paneles del StackPane */
    private void mostrarPanel(javafx.scene.Node panelVisible) {
        tablaResultados.setVisible(panelVisible == tablaResultados);
        if (panelLexico  != null) panelLexico .setVisible(panelVisible == panelLexico);
        if (panelCodigo  != null) panelCodigo .setVisible(panelVisible == panelCodigo);
    }

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
    //  PANEL LÉXICO — vista por cláusula
    // ══════════════════════════════════════════════════════════

    // ══════════════════════════════════════════════════════════
    //  PANEL SINTÁCTICO — árbol AST + cláusulas
    // ══════════════════════════════════════════════════════════

    private void construirPanelSintactico(
            org.example.csvquery.models.ast.NodoConsulta ast,
            List<Token> tokens,
            List<Token> errores) {

        if (vboxLexico == null) return;
        vboxLexico.getChildren().clear();

        // ── Barra de resumen ──────────────────────────────────────────────
        int clausulas = (int) tokens.stream().filter(this::esInicioClausula).count();
        boolean hayErrores = !errores.isEmpty() || ast == null;
        HBox resumen = new HBox(12);
        resumen.setPadding(new Insets(0, 0, 12, 0));
        resumen.getChildren().addAll(
                crearStatCard(String.valueOf(tokens.size()),  "tokens",         false),
                crearStatCard(String.valueOf(clausulas),      "cláusulas",      false),
                crearStatCard(hayErrores ? "NO" : "SÍ",      "AST válido",     hayErrores),
                crearStatCard(hayErrores ? String.valueOf(errores.size()) : "0",
                        "errores",  hayErrores)
        );
        vboxLexico.getChildren().add(resumen);

        // ── Árbol del AST ─────────────────────────────────────────────────
        Label lblArbol = new Label("Árbol de Sintaxis Abstracta (AST)");
        lblArbol.getStyleClass().add("clausula-nombre");
        lblArbol.setPadding(new Insets(0, 0, 6, 0));
        vboxLexico.getChildren().add(lblArbol);

        if (ast == null) {
            VBox sinArbol = new VBox(4);
            sinArbol.setPadding(new Insets(10, 14, 10, 14));
            sinArbol.getStyleClass().add("clausula-error");
            Label msg = new Label("El AST no se pudo construir — revisa los errores léxicos o sintácticos.");
            msg.getStyleClass().add("clausula-desc");
            sinArbol.getChildren().add(msg);
            vboxLexico.getChildren().add(sinArbol);
        } else {
            org.example.csvquery.models.ast.NodoInfo raiz = ast.toInfo();
            VBox arbolBox = new VBox(4);
            arbolBox.setPadding(new Insets(10, 14, 10, 14));
            arbolBox.getStyleClass().add("clausula-ok");
            renderNodo(raiz, arbolBox, 0);
            vboxLexico.getChildren().add(arbolBox);
        }

        // ── Separador ─────────────────────────────────────────────────────
        javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
        sep.setPadding(new Insets(8, 0, 8, 0));
        vboxLexico.getChildren().add(sep);

        // ── Cláusulas agrupadas ───────────────────────────────────────────
        Label lblClausulas = new Label("Tokens agrupados por cláusula");
        lblClausulas.getStyleClass().add("clausula-nombre");
        lblClausulas.setPadding(new Insets(0, 0, 6, 0));
        vboxLexico.getChildren().add(lblClausulas);

        if (tokens.isEmpty()) {
            Label empty = new Label("No hay tokens que mostrar.");
            empty.getStyleClass().add("lbl-empty");
            vboxLexico.getChildren().add(empty);
            return;
        }

        // Agrupar tokens por cláusula
        List<List<Token>> grupos       = new ArrayList<>();
        List<String>      nombresGrupo = new ArrayList<>();
        List<Token>       grupoActual  = null;
        String            nombreActual = "";

        for (Token t : tokens) {
            if (esInicioClausula(t)) {
                if (grupoActual != null) { grupos.add(grupoActual); nombresGrupo.add(nombreActual); }
                grupoActual  = new ArrayList<>();
                nombreActual = t.getLexema().toUpperCase();
            }
            if (grupoActual != null) grupoActual.add(t);
        }
        if (grupoActual != null) { grupos.add(grupoActual); nombresGrupo.add(nombreActual); }

        Set<String> lexemasError = errores.stream().map(Token::getLexema).collect(java.util.stream.Collectors.toSet());

        for (int g = 0; g < grupos.size(); g++) {
            List<Token> grupo      = grupos.get(g);
            String      nombre     = nombresGrupo.get(g);
            boolean     tieneError = grupo.stream()
                    .anyMatch(t -> lexemasError.contains(t.getLexema()) || t.getId() >= 5000);

            VBox clausulaBox = new VBox(6);
            clausulaBox.setPadding(new Insets(10, 14, 10, 14));
            clausulaBox.getStyleClass().add(tieneError ? "clausula-error" : "clausula-ok");

            HBox header = new HBox(8);
            header.setAlignment(Pos.CENTER_LEFT);
            Label lblNombre = new Label(nombre);
            lblNombre.getStyleClass().add("clausula-nombre");
            Label badge = new Label(tieneError ? "⚠ ERROR" : "✓ OK");
            badge.getStyleClass().add(tieneError ? "badge-error" : "badge-ok");
            header.getChildren().addAll(lblNombre, badge);

            Label desc = new Label(descripcionClausula(nombre));
            desc.getStyleClass().add("clausula-desc");

            FlowPane chips = new FlowPane(6, 6);
            chips.setPadding(new Insets(6, 0, 0, 0));
            for (Token t : grupo) {
                boolean esErr = lexemasError.contains(t.getLexema()) || t.getId() >= 5000;
                chips.getChildren().add(crearChip(t, esErr));
            }
            clausulaBox.getChildren().addAll(header, desc, chips);
            vboxLexico.getChildren().add(clausulaBox);
        }

        // ── Sección de errores ────────────────────────────────────────────
        if (!errores.isEmpty()) {
            VBox errBox = new VBox(8);
            errBox.setPadding(new Insets(10, 14, 10, 14));
            errBox.getStyleClass().add("clausula-error");
            Label titulo = new Label("Errores léxicos detectados");
            titulo.getStyleClass().add("clausula-nombre");
            errBox.getChildren().add(titulo);
            for (Token e : errores) {
                HBox fila = new HBox(10);
                fila.setAlignment(Pos.CENTER_LEFT);
                Label lBadge = new Label(e.getNombre());  lBadge.getStyleClass().add("badge-error");
                Label lLex   = new Label("'" + e.getLexema() + "'"); lLex.getStyleClass().add("chip-lexema-error");
                Label lDesc  = new Label(descripcionError(e.getNombre())); lDesc.getStyleClass().add("clausula-desc");
                fila.getChildren().addAll(lBadge, lLex, lDesc);
                errBox.getChildren().add(fila);
            }
            vboxLexico.getChildren().add(errBox);
        }
    }

    /**
     * Renderiza recursivamente un NodoInfo como filas indentadas dentro del VBox del árbol.
     * Cada nivel de profundidad agrega 20px de indentación.
     */
    private void renderNodo(org.example.csvquery.models.ast.NodoInfo nodo, VBox contenedor, int nivel) {
        HBox fila = new HBox(8);
        fila.setAlignment(Pos.CENTER_LEFT);
        fila.setPadding(new Insets(2, 0, 2, nivel * 20));

        // Conector visual
        if (nivel > 0) {
            Label conector = new Label(nodo.hijos.isEmpty() ? "└─" : "├─");
            conector.getStyleClass().add("ast-conector");
            fila.getChildren().add(conector);
        }

        // Caja del nodo
        VBox caja = new VBox(1);
        caja.setPadding(new Insets(3, 8, 3, 8));
        caja.getStyleClass().add(nodo.presente ? estiloNodo(nodo.categoria) : "ast-nodo-ausente");

        Label lblEtiqueta = new Label(nodo.etiqueta);
        lblEtiqueta.getStyleClass().add("ast-etiqueta");

        caja.getChildren().add(lblEtiqueta);
        if (!nodo.detalle.isEmpty()) {
            Label lblDetalle = new Label(nodo.detalle);
            lblDetalle.getStyleClass().add("ast-detalle");
            caja.getChildren().add(lblDetalle);
        }

        fila.getChildren().add(caja);
        contenedor.getChildren().add(fila);

        // Hijos recursivos
        for (org.example.csvquery.models.ast.NodoInfo hijo : nodo.hijos) {
            renderNodo(hijo, contenedor, nivel + 1);
        }
    }

    private String estiloNodo(org.example.csvquery.models.ast.NodoInfo.Categoria cat) {
        return switch (cat) {
            case CONSULTA   -> "ast-nodo-consulta";
            case CLAUSULA   -> "ast-nodo-clausula";
            case COLUMNA    -> "ast-nodo-columna";
            case LITERAL    -> "ast-nodo-literal";
            case OPERADOR   -> "ast-nodo-operador";
            case AGREGACION -> "ast-nodo-agregacion";
            case OPCIONAL   -> "ast-nodo-ausente";
        };
    }

    private void construirPanelLexico(List<Token> tokens, List<Token> errores) {
        if (vboxLexico == null) return;
        vboxLexico.getChildren().clear();

        if (tokens == null || tokens.isEmpty()) {
            Label empty = new Label("Ejecuta una consulta para ver el análisis léxico.");
            empty.getStyleClass().add("lbl-empty");
            vboxLexico.getChildren().add(empty);
            return;
        }

        // ── Barra de resumen ──────────────────────────────────────────────
        HBox resumen = new HBox(12);
        resumen.setPadding(new Insets(0, 0, 12, 0));
        resumen.getChildren().addAll(
                crearStatCard(String.valueOf(tokens.size()),  "tokens válidos",  false),
                crearStatCard(String.valueOf(errores.size()), "errores léxicos", !errores.isEmpty()),
                crearStatCard(String.valueOf(contarClausulas(tokens)), "cláusulas", false)
        );
        vboxLexico.getChildren().add(resumen);

        // ── Agrupar tokens por cláusula ───────────────────────────────────
        List<List<Token>> grupos       = new ArrayList<>();
        List<String>      nombresGrupo = new ArrayList<>();
        List<Token>       grupoActual  = null;
        String            nombreActual = "";

        for (Token t : tokens) {
            if (esInicioClausula(t)) {
                if (grupoActual != null) {
                    grupos.add(grupoActual);
                    nombresGrupo.add(nombreActual);
                }
                grupoActual  = new ArrayList<>();
                nombreActual = t.getLexema().toUpperCase();
            }
            if (grupoActual != null) grupoActual.add(t);
        }
        if (grupoActual != null) {
            grupos.add(grupoActual);
            nombresGrupo.add(nombreActual);
        }

        Set<String> lexemasError = errores.stream()
                .map(Token::getLexema)
                .collect(Collectors.toSet());

        // ── Renderizar cada cláusula ──────────────────────────────────────
        for (int g = 0; g < grupos.size(); g++) {
            List<Token> grupo      = grupos.get(g);
            String      nombre     = nombresGrupo.get(g);
            boolean     tieneError = grupo.stream()
                    .anyMatch(t -> lexemasError.contains(t.getLexema()) || t.getId() >= 5000);

            VBox clausulaBox = new VBox(6);
            clausulaBox.setPadding(new Insets(10, 14, 10, 14));
            clausulaBox.getStyleClass().add(tieneError ? "clausula-error" : "clausula-ok");

            // Cabecera: nombre + badge de estado
            HBox header = new HBox(8);
            header.setAlignment(Pos.CENTER_LEFT);
            Label lblNombre = new Label(nombre);
            lblNombre.getStyleClass().add("clausula-nombre");
            Label badge = new Label(tieneError ? "⚠ ERROR" : "✓ OK");
            badge.getStyleClass().add(tieneError ? "badge-error" : "badge-ok");
            header.getChildren().addAll(lblNombre, badge);

            // Descripción semántica de la cláusula
            Label desc = new Label(descripcionClausula(nombre));
            desc.getStyleClass().add("clausula-desc");

            // Chips de tokens en FlowPane (se adaptan al ancho)
            FlowPane chips = new FlowPane(6, 6);
            chips.setPadding(new Insets(6, 0, 0, 0));
            for (Token t : grupo) {
                boolean esErr = lexemasError.contains(t.getLexema()) || t.getId() >= 5000;
                chips.getChildren().add(crearChip(t, esErr));
            }

            clausulaBox.getChildren().addAll(header, desc, chips);
            vboxLexico.getChildren().add(clausulaBox);
        }

        // ── Sección de errores léxicos ────────────────────────────────────
        if (!errores.isEmpty()) {
            VBox errBox = new VBox(8);
            errBox.setPadding(new Insets(10, 14, 10, 14));
            errBox.getStyleClass().add("clausula-error");

            Label titulo = new Label("Errores léxicos detectados");
            titulo.getStyleClass().add("clausula-nombre");
            errBox.getChildren().add(titulo);

            for (Token e : errores) {
                HBox fila = new HBox(10);
                fila.setAlignment(Pos.CENTER_LEFT);

                Label lBadge = new Label(e.getNombre());
                lBadge.getStyleClass().add("badge-error");

                Label lLex = new Label("'" + e.getLexema() + "'");
                lLex.getStyleClass().add("chip-lexema-error");

                Label lDesc = new Label(descripcionError(e.getNombre()));
                lDesc.getStyleClass().add("clausula-desc");

                fila.getChildren().addAll(lBadge, lLex, lDesc);
                errBox.getChildren().add(fila);
            }
            vboxLexico.getChildren().add(errBox);
        }
    }

    // ── Helpers del panel léxico ──────────────────────────────────────────────

    private VBox crearStatCard(String valor, String etiqueta, boolean peligro) {
        VBox card = new VBox(2);
        card.setPadding(new Insets(8, 16, 8, 16));
        card.getStyleClass().add(peligro ? "stat-card-danger" : "stat-card");
        Label lVal = new Label(valor);
        lVal.getStyleClass().add(peligro ? "stat-val-danger" : "stat-val");
        Label lEtq = new Label(etiqueta);
        lEtq.getStyleClass().add("stat-lbl");
        card.getChildren().addAll(lVal, lEtq);
        return card;
    }

    private VBox crearChip(Token t, boolean esError) {
        VBox chip = new VBox(2);
        chip.setPadding(new Insets(5, 10, 5, 10));
        chip.getStyleClass().add(esError ? "token-chip-error" : categoriaChip(t));
        Label lLexema = new Label(t.getLexema());
        lLexema.getStyleClass().add("chip-lexema");
        Label lTipo = new Label(nombreCorto(t.getNombre()));
        lTipo.getStyleClass().add("chip-tipo");
        chip.getChildren().addAll(lLexema, lTipo);
        return chip;
    }

    private boolean esInicioClausula(Token t) {
        return switch (t.getNombre()) {
            case "TOKEN_TRAER", "TOKEN_DESDE", "TOKEN_DONDE",
                 "TOKEN_ORDENAR", "TOKEN_LIMITAR", "TOKEN_GUARDAR" -> true;
            default -> false;
        };
    }

    private int contarClausulas(List<Token> tokens) {
        return (int) tokens.stream().filter(this::esInicioClausula).count();
    }

    private String categoriaChip(Token t) {
        if (t.getId() >= 2000 && t.getId() < 3000) return "token-chip-kw";
        if (t.getId() >= 1000 && t.getId() < 2000) return "token-chip-op";
        if (t.getId() >= 3000 && t.getId() < 4000) return "token-chip-sym";
        if (t.getId() >= 4000 && t.getId() < 5000) return "token-chip-val";
        return "token-chip-error";
    }

    private String nombreCorto(String nombre) {
        return nombre.replace("TOKEN_", "").replace("ERROR_", "ERR_").replace("_", " ");
    }

    private String descripcionClausula(String nombre) {
        return switch (nombre) {
            case "TRAER"   -> "Columnas o función de agregación a obtener";
            case "DESDE"   -> "Archivo CSV de origen de los datos";
            case "DONDE"   -> "Condición de filtrado sobre los registros";
            case "ORDENAR" -> "Columna y dirección de ordenamiento";
            case "LIMITAR" -> "Número máximo de registros a devolver";
            case "GUARDAR" -> "Archivo de destino para guardar el resultado";
            default        -> "";
        };
    }

    private String descripcionError(String nombre) {
        return switch (nombre) {
            case "ERROR_OPERADOR_INCOMPLETO" -> "Se esperaba '!=' pero falta el '='";
            case "ERROR_CADENA_SIN_CERRAR"   -> "La cadena de texto no tiene cierre con \"";
            case "ERROR_NUMERO_INVALIDO"     -> "Formato numérico incorrecto (ej: 18. o 3.1.4)";
            default                          -> "Token no reconocido por el autómata";
        };
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
        editorSQL.caretPositionProperty().addListener((obs, oldPos, newPos) -> {
            // En RichTextFX, los párrafos (líneas) y columnas empiezan en 0, por eso sumamos 1
            int linea = editorSQL.getCurrentParagraph() + 1;
            int col = editorSQL.getCaretColumn() + 1;

            lblCursor.setText("LÍNEA " + linea + ", COL " + col);
        });
    }

    // ══════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════

    private void setEstado(String texto, String cssClass) {
        lblEstado.getStyleClass().removeAll("lbl-estado", "lbl-estado-error", "lbl-estado-running");
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