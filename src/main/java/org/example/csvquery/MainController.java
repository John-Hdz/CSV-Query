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

    // ── Panel consola de compilación ──────────────────────────
    @FXML private VBox       panelConsola;
    @FXML private VBox       vboxConsola;
    @FXML private ScrollPane consolaScroll;

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
    private org.example.csvquery.models.ast.NodoAST ultimoAST = null;

    private enum TipoEntrada { SISTEMA, OK, INFO, ERROR, SEPARADOR }

    private record EntradaConsola(TipoEntrada tipo, int errorId, String mensaje) {
        static EntradaConsola sistema(String msg)              { return new EntradaConsola(TipoEntrada.SISTEMA,   0, msg); }
        static EntradaConsola ok(String msg)                   { return new EntradaConsola(TipoEntrada.OK,        0, msg); }
        static EntradaConsola info(String msg)                 { return new EntradaConsola(TipoEntrada.INFO,      0, msg); }
        static EntradaConsola error(int id, String msg)        { return new EntradaConsola(TipoEntrada.ERROR,  id, msg); }
        static EntradaConsola separador()                      { return new EntradaConsola(TipoEntrada.SEPARADOR, 0, ""); }
    }

    private final List<EntradaConsola> consolaEntradas = new ArrayList<>();

    private static final String RUTA_AUTOMATA =
            "src/main/resources/CSV/Matriz de transicion 2.csv";

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

    // palabras reservadas y funciones
    private static final String[] KEYWORDS = new String[] {
            "TRAER", "DESDE", "DONDE", "Y", "O", "ORDENAR", "POR", "ASC", "DESC", "LIMITAR", "DISTINTO", "GUARDAR", "EN", "INSERTAR", "VALORES", "ACTUALIZAR", "ELIMINAR"
    };
    private static final String[] FUNCTIONS = new String[] {
            "CONTAR", "SUMA", "PROMEDIO", "MAXIMO", "MINIMO"
    };

    private static final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
    private static final String FUNCTION_PATTERN = "\\b(" + String.join("|", FUNCTIONS) + ")\\b";
    private static final String STRING_PATTERN = "\"([^\"]*)\""; // Para detectar textos entre comillas

    private static final Pattern PATTERN = Pattern.compile(
            "(?<KEYWORD>" + KEYWORD_PATTERN + ")"
                    + "|(?<FUNCTION>" + FUNCTION_PATTERN + ")"
                    + "|(?<STRING>" + STRING_PATTERN + ")"
    );
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
        consolaEntradas.clear();
        if (vboxLexico  != null) vboxLexico.getChildren().clear();
        if (vboxConsola != null) vboxConsola.getChildren().clear();
        if (areaCodigo  != null) areaCodigo.clear();
        setEstado("LISTO", "lbl-estado");
        lblCursor.setText("LÍNEA 1, COL 1");
        actualizarNumerosLinea(1);
        mostrarPanel(tablaResultados);
        activarTab(tabResultado, tabLexico, tabTokens, tabConsola, tabCodigo);
    }

    //  EJECUTAR CONSULTA

    @FXML
    public void onEjecutarConsulta() {
        String texto = editorSQL.getText().trim();

        if (texto.isEmpty()) {
            alerta("Consulta vacía", "Escribe una consulta antes de ejecutar.");
            return;
        }

        consolaEntradas.clear();
        logConsola.clear();
        ultimoScriptPy = "";

        setEstado("ANALIZANDO...", "lbl-estado-running");

        // ── Archivo temporal para el Lexer ───────────────────────────────
        File tempQuery = null;
        try {
            tempQuery = File.createTempFile("csvquery_input_", ".txt");
            tempQuery.deleteOnExit();
            String sinComentarios = texto.replaceAll("--[^\n]*", "").trim();
            try (FileWriter fw = new FileWriter(tempQuery)) { fw.write(sinComentarios); }
        } catch (IOException e) {
            alerta("Error", "No se pudo crear archivo temporal: " + e.getMessage());
            setEstado("ERROR", "lbl-estado-error");
            return;
        }

        // ── Análisis Léxico ───────────────────────────────────────────────
        consolaEntradas.add(EntradaConsola.sistema("Iniciando análisis léxico..."));

        Lexer lexer = new Lexer(RUTA_AUTOMATA);
        lexer.analizarArchivo(tempQuery.getAbsolutePath());
        tempQuery.delete();

        ultimosTokens  = lexer.getTablaSimbolos();
        ultimosErrores = lexer.getPilaErrores();

        consolaEntradas.add(EntradaConsola.ok(
                "Autómata cargado — " + ultimosTokens.size() + " token(s) reconocido(s)"));

        construirPanelSintactico(ultimoAST, ultimosTokens, ultimosErrores);

        if (!ultimosErrores.isEmpty()) {
            consolaEntradas.add(EntradaConsola.separador());
            for (Token e : ultimosErrores) {
                int id = clasificarErrorLexico(e.getNombre());
                consolaEntradas.add(EntradaConsola.error(id,
                        "'" + e.getLexema() + "' — " + descripcionError(e.getNombre())));
                log("ERROR", "[" + id + "] " + e.getNombre() + ": '" + e.getLexema() + "'");
            }
            setEstado("ERROR LÉXICO", "lbl-estado-error");
            mostrarPanel(panelLexico);
            activarTab(tabLexico, tabResultado, tabTokens, tabConsola, tabCodigo);
            return;
        }

        consolaEntradas.add(EntradaConsola.separador());

        // ── Análisis Sintáctico ───────────────────────────────────────────
        consolaEntradas.add(EntradaConsola.sistema("Iniciando análisis sintáctico..."));
        setEstado("ANÁLISIS SINTÁCTICO...", "lbl-estado-running");

        org.example.csvquery.models.ast.NodoAST ast;
        try {
            Parser parser = new Parser(ultimosTokens);
            ast = parser.parsear();
            ultimoAST = ast;
            consolaEntradas.add(EntradaConsola.ok("AST construido correctamente"));
            log("INFO", "AST construido correctamente.");
        } catch (Parser.ParseException e) {
            int id = 5100 + clasificarErrorSintactico(e.getMessage());
            consolaEntradas.add(EntradaConsola.error(id, e.getMessage()));
            log("ERROR", "[" + id + "] " + e.getMessage());
            setEstado("ERROR SINTÁCTICO", "lbl-estado-error");
            mostrarPanel(panelConsola);
            activarTab(tabConsola, tabLexico, tabResultado, tabTokens, tabCodigo);
            renderizarConsola();
            return;
        }

        consolaEntradas.add(EntradaConsola.separador());

        // ── Análisis Semántico ────────────────────────────────────────────
        consolaEntradas.add(EntradaConsola.sistema("Iniciando análisis semántico..."));
        setEstado("ANÁLISIS SEMÁNTICO...", "lbl-estado-running");

        try {
            ast.validarSemantica();
            consolaEntradas.add(EntradaConsola.ok("Validación semántica exitosa"));
            log("INFO", "Validación semántica exitosa.");
        } catch (Exception e) {
            int id = 5200 + clasificarErrorSemantico(e.getMessage());
            consolaEntradas.add(EntradaConsola.error(id, e.getMessage()));
            log("ERROR", "[" + id + "] " + e.getMessage());
            setEstado("ERROR SEMÁNTICO", "lbl-estado-error");
            mostrarPanel(panelConsola);
            activarTab(tabConsola, tabLexico, tabResultado, tabTokens, tabCodigo);
            renderizarConsola();
            return;
        }

        consolaEntradas.add(EntradaConsola.separador());

        // ── Generación de código ──────────────────────────────────────────
        consolaEntradas.add(EntradaConsola.sistema("Generando código intermedio..."));
        setEstado("GENERANDO SCRIPT...", "lbl-estado-running");
        ultimoScriptPy = ast.generarPython();
        consolaEntradas.add(EntradaConsola.ok("Script Python generado — "
                + ultimoScriptPy.lines().count() + " línea(s)"));
        for (String linea : ultimoScriptPy.split("\n")) log("SCRIPT", linea);

        consolaEntradas.add(EntradaConsola.separador());

        // ── Ejecución ─────────────────────────────────────────────────────
        consolaEntradas.add(EntradaConsola.sistema("Ejecutando script con Pandas..."));
        setEstado("EJECUTANDO...", "lbl-estado-running");

        PandasRunner.Resultado resultado = PandasRunner.ejecutar(ultimoScriptPy, 30);
        if (!resultado.exitoso()) {
            int id = 5210; // error de ejecución Python
            consolaEntradas.add(EntradaConsola.error(id, resultado.error()));
            log("ERROR", "[" + id + "] " + resultado.error());
            alerta("Error en Python", resultado.error());
            setEstado("ERROR PYTHON", "lbl-estado-error");
            return;
        }

        log("INFO", "Consulta ejecutada con éxito.");
        consolaEntradas.add(EntradaConsola.ok("Consulta ejecutada con éxito"));

        // ── Mostrar resultado ─────────────────────────────────────────────
        String salida = resultado.salida();
        if (!salida.contains(",") && !salida.contains("\n")) mostrarEscalar(salida);
        else mostrarResultadoCSV(salida);

        setEstado("LISTO", "lbl-estado");
        mostrarPanel(tablaResultados);
        activarTab(tabResultado, tabLexico, tabTokens, tabConsola, tabCodigo);
    }

    // ── Clasificadores de error ────────────────────────────────────────────────

    /** Devuelve el ID léxico (serie 5000) según el tipo de error del token */
    private int clasificarErrorLexico(String nombreToken) {
        return switch (nombreToken) {
            case "ERROR_OPERADOR_INCOMPLETO" -> 5001;
            case "ERROR_CADENA_SIN_CERRAR"   -> 5002;
            case "ERROR_NUMERO_INVALIDO"     -> 5003;
            default                          -> 5000;
        };
    }

    private int clasificarErrorSintactico(String mensaje) {
        if (mensaje == null) return 0;
        String m = mensaje.toLowerCase();
        if (m.contains("traer"))                       return 1;  // 5101 falta TRAER
        if (m.contains("desde"))                       return 2;  // 5102 falta DESDE
        if (m.contains("archivo") || m.contains("csv")) return 3; // 5103 falta ruta CSV
        if (m.contains("operador de comparación"))     return 4;  // 5104 operador inválido
        if (m.contains("paréntesis"))                  return 5;  // 5105 paréntesis sin cerrar
        if (m.contains("punto y coma") || m.contains(";")) return 6; // 5106 falta ;
        if (m.contains("columna"))                     return 7;  // 5107 columna inválida
        if (m.contains("literal") || m.contains("valor")) return 8; // 5108 literal inválido
        return 0;                                                  // 5100 genérico
    }

    private int clasificarErrorSemantico(String mensaje) {
        if (mensaje == null) return 0;
        String m = mensaje.toLowerCase();
        if (m.contains("columna") && m.contains("no existe")) return 1; // 5201 columna inexistente
        if (m.contains("archivo") && m.contains("no exist"))  return 2; // 5202 archivo no existe
        if (m.contains("operador") && m.contains("cadena"))   return 3; // 5203 operador orden en cadena
        if (m.contains("comparar"))                            return 4; // 5204 tipos incompatibles
        if (m.contains("aritmético") || m.contains("numérico")) return 5; // 5205 op aritmético en no-número
        if (m.contains("lógico") || m.contains("booleano"))   return 6; // 5206 op lógico mal tipado
        if (m.contains("agregación") || m.contains("contar")
                || m.contains("suma") || m.contains("promedio"))     return 7; // 5207 error de agregación
        return 0;                                                        // 5200 genérico
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
        mostrarPanel(panelConsola);
        activarTab(tabConsola, tabLexico, tabResultado, tabTokens, tabCodigo);
        renderizarConsola();
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

    /** Renderiza consolaEntradas en el VBox de la consola visual */
    private void renderizarConsola() {
        if (vboxConsola == null) return;
        vboxConsola.getChildren().clear();

        if (consolaEntradas.isEmpty()) {
            Label vacio = new Label("  Ejecuta una consulta para ver los resultados de compilación.");
            vacio.getStyleClass().add("consola-info");
            vboxConsola.getChildren().add(vacio);
            return;
        }

        for (EntradaConsola e : consolaEntradas) {
            switch (e.tipo()) {
                case SEPARADOR -> {
                    javafx.scene.control.Separator sep = new javafx.scene.control.Separator();
                    sep.getStyleClass().add("consola-sep");
                    sep.setPadding(new javafx.geometry.Insets(4, 0, 4, 0));
                    vboxConsola.getChildren().add(sep);
                }
                case SISTEMA -> {
                    HBox fila = new HBox(8);
                    fila.getStyleClass().add("consola-fila-sistema");
                    Label prompt = new Label("$");           prompt.getStyleClass().add("consola-prompt");
                    Label msg    = new Label(e.mensaje());   msg.getStyleClass().add("consola-sistema");
                    fila.getChildren().addAll(prompt, msg);
                    vboxConsola.getChildren().add(fila);
                }
                case OK -> {
                    HBox fila = new HBox(8);
                    fila.getStyleClass().add("consola-fila-ok");
                    Label ico = new Label("✓");           ico.getStyleClass().add("consola-ico-ok");
                    Label msg = new Label(e.mensaje());   msg.getStyleClass().add("consola-ok");
                    fila.getChildren().addAll(ico, msg);
                    vboxConsola.getChildren().add(fila);
                }
                case INFO -> {
                    HBox fila = new HBox(8);
                    fila.getStyleClass().add("consola-fila-info");
                    Label ico = new Label("·");           ico.getStyleClass().add("consola-ico-info");
                    Label msg = new Label(e.mensaje());   msg.getStyleClass().add("consola-info");
                    fila.getChildren().addAll(ico, msg);
                    vboxConsola.getChildren().add(fila);
                }
                case ERROR -> {
                    VBox bloque = new VBox(4);
                    bloque.getStyleClass().add("consola-bloque-error");
                    bloque.setPadding(new javafx.geometry.Insets(8, 12, 8, 12));

                    HBox cabecera = new HBox(8);
                    cabecera.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                    Label ico   = new Label("✕");                     ico.getStyleClass().add("consola-ico-error");
                    Label badge = new Label("ERR " + e.errorId());    badge.getStyleClass().add("consola-badge-error");
                    Label serie = new Label(serieError(e.errorId())); serie.getStyleClass().add("consola-serie");
                    cabecera.getChildren().addAll(ico, badge, serie);

                    Label msg = new Label(e.mensaje());
                    msg.getStyleClass().add("consola-error-msg");
                    msg.setWrapText(true);

                    bloque.getChildren().addAll(cabecera, msg);
                    vboxConsola.getChildren().add(bloque);
                }
            }
        }

        // Auto-scroll al fondo
        consolaScroll.layout();
        consolaScroll.setVvalue(1.0);
    }

    private String serieError(int id) {
        if (id >= 5200) return "SEMÁNTICO";
        if (id >= 5100) return "SINTÁCTICO";
        return "LÉXICO";
    }

    /** Alterna visibilidad entre los paneles del StackPane */
    private void mostrarPanel(javafx.scene.Node panelVisible) {
        tablaResultados.setVisible(panelVisible == tablaResultados);
        if (panelLexico  != null) panelLexico .setVisible(panelVisible == panelLexico);
        if (panelCodigo  != null) panelCodigo .setVisible(panelVisible == panelCodigo);
        if (panelConsola != null) panelConsola.setVisible(panelVisible == panelConsola);
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
    //  PANEL SINTÁCTICO — árbol AST + cláusulas
    // ══════════════════════════════════════════════════════════

    private void construirPanelSintactico(
            org.example.csvquery.models.ast.NodoAST ast,
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