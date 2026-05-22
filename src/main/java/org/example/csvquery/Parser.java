package org.example.csvquery;

import org.example.csvquery.models.Token;
import org.example.csvquery.models.ast.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Análisis Sintáctico: convierte la lista plana de tokens producida por el Lexer
 * en un Árbol de Sintaxis Abstracta (AST) formado por nodos NodoAST.
 *
 * Gramática soportada:
 *
 *   consulta   → TRAER seleccion DESDE STRING [donde] [ordenar] [limitar] [guardar] PUNTOCOMA
 *   seleccion  → DISTINTO? ( ASTERISCO | agregacion | col {COMA col}* )
 *   agregacion → (CONTAR|SUMA|PROMEDIO|MAXIMO|MINIMO) PAREN_ABRE (ASTERISCO|ID) PAREN_CIERRA
 *   donde      → DONDE condicion
 *   condicion  → comparacion { (Y|O) comparacion }*
 *   comparacion→ col OPERADOR literal
 *   ordenar    → ORDENAR POR col (ASC|DESC)?
 *   limitar    → LIMITAR ENTERO
 *   guardar    → GUARDAR EN STRING
 *
 * Si la estructura no coincide con la gramática lanza ParseException (error sintáctico).
 */
public class Parser {

    // ── Excepción propia para errores sintácticos ──────────────────────────────
    public static class ParseException extends Exception {
        public ParseException(String mensaje) { super(mensaje); }
    }

    // ── Estado interno ─────────────────────────────────────────────────────────
    private final List<Token> tokens;
    private int pos = 0;

    public Parser(List<Token> tokens) {
        // Filtramos el TOKEN_PUNTOCOMA del análisis (lo consumimos al final)
        this.tokens = tokens;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  PUNTO DE ENTRADA
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Construye y devuelve el NodoConsulta raíz del AST.
     * Lanza ParseException si la consulta tiene errores sintácticos.
     */
    public NodoConsulta parsear() throws ParseException {

        // ── TRAER ──────────────────────────────────────────────────────────────
        consumir("TOKEN_TRAER");

        // ── DISTINTO (opcional) ────────────────────────────────────────────────
        boolean distinto = consumirSi("TOKEN_DISTINTO");

        // ── Selección de columnas o función de agregación ─────────────────────
        NodoSeleccion nodoSeleccion = parsearSeleccion(distinto);

        // ── DESDE ──────────────────────────────────────────────────────────────
        consumir("TOKEN_DESDE");
        String rutaCSV = valorActual();
        consumir("TOKEN_STRING");
        NodoDesde nodoDesde = new NodoDesde(rutaCSV);

        // ── DONDE (opcional) ───────────────────────────────────────────────────
        NodoDonde nodoDonde = null;
        if (hayMas() && esTipo("TOKEN_DONDE")) {
            avanzar();
            NodoAST condicion = parsearCondicion();
            nodoDonde = new NodoDonde(condicion);
        }

        // ── ORDENAR POR (opcional) ─────────────────────────────────────────────
        NodoOrdenar nodoOrdenar = null;
        if (hayMas() && esTipo("TOKEN_ORDENAR")) {
            avanzar();
            consumir("TOKEN_POR");
            String colOrden = valorActual();
            consumirIdentificador();
            boolean asc = true;
            if (hayMas() && esTipo("TOKEN_DESC")) { asc = false; avanzar(); }
            else if (hayMas() && esTipo("TOKEN_ASC")) avanzar();
            nodoOrdenar = new NodoOrdenar(colOrden, asc);
        }

        // ── LIMITAR (opcional) ────────────────────────────────────────────────
        NodoLimitar nodoLimitar = null;
        if (hayMas() && esTipo("TOKEN_LIMITAR")) {
            avanzar();
            if (!esTipo("TOKEN_ENTERO"))
                throw new ParseException("Se esperaba un número entero después de LIMITAR, pero se encontró: '" + valorActual() + "'");
            int n = Integer.parseInt(valorActual());
            avanzar();
            nodoLimitar = new NodoLimitar(n);
        }

        // ── GUARDAR EN (opcional) ─────────────────────────────────────────────
        NodoGuardar nodoGuardar = null;
        if (hayMas() && esTipo("TOKEN_GUARDAR")) {
            avanzar();
            consumir("TOKEN_EN");
            String rutaSalida = valorActual();
            consumir("TOKEN_STRING");
            nodoGuardar = new NodoGuardar(rutaSalida);
        }

        // ── PUNTOCOMA (obligatorio al final) ─────────────────────────────────
        if (hayMas() && esTipo("TOKEN_PUNTOCOMA")) {
            avanzar();
        }
        // Si quedan tokens sobrantes, es un error sintáctico
        if (hayMas()) {
            throw new ParseException(
                    "Token inesperado después del fin de la consulta: '" + valorActual() + "'");
        }

        return new NodoConsulta(nodoSeleccion, nodoDesde, nodoDonde, nodoOrdenar, nodoLimitar, nodoGuardar);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SELECCIÓN
    // ══════════════════════════════════════════════════════════════════════════

    private NodoSeleccion parsearSeleccion(boolean distinto) throws ParseException {

        // ¿Función de agregación?
        if (esAgregacion(tipo())) {
            NodoAgregacion agg = parsearAgregacion();
            return new NodoSeleccion(List.of(), false, distinto, agg);
        }

        // ¿Asterisco (todas las columnas)?
        if (esTipo("TOKEN_ASTERISCO") || esTipo("TOKEN_TODO")) {
            avanzar();
            return new NodoSeleccion(List.of(), true, distinto, null);
        }

        // Lista de columnas: col {, col}*
        List<String> columnas = new ArrayList<>();
        columnas.add(consumirNombreColumna());

        while (hayMas() && esTipo("TOKEN_COMA")) {
            avanzar();
            columnas.add(consumirNombreColumna());
        }

        return new NodoSeleccion(columnas, false, distinto, null);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  AGREGACIÓN
    // ══════════════════════════════════════════════════════════════════════════

    private NodoAgregacion parsearAgregacion() throws ParseException {
        String funcion = tipo(); avanzar();
        consumir("TOKEN_PARENTESIS_ABRE");

        String arg;
        if (esTipo("TOKEN_ASTERISCO")) { arg = "*"; avanzar(); }
        else                           { arg = consumirNombreColumna(); }

        consumir("TOKEN_PARENTESIS_CIERRA");
        return new NodoAgregacion(funcion, arg);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  CONDICIÓN  —  soporta agrupación lógica con paréntesis
    //
    //  condicion  → termino { (Y|O) termino }*
    //  termino    → PAREN_ABRE condicion PAREN_CIERRA   ← agrupación lógica
    //             | comparacion
    // ══════════════════════════════════════════════════════════════════════════

    private NodoAST parsearCondicion() throws ParseException {
        NodoAST nodo = parsearTermino();

        while (hayMas() && (esTipo("TOKEN_Y") || esTipo("TOKEN_O"))) {
            String operador = esTipo("TOKEN_Y") ? "Y" : "O";
            avanzar();
            nodo = new NodoOperacionBinaria(nodo, operador, parsearTermino());
        }
        return nodo;
    }

    /**
     * Un "término" es una comparación simple o una condición lógica entre paréntesis.
     *
     *  (Ciudad = "Madrid" O Dept = "X")  ← agrupación LÓGICA  → parsearTermino la consume
     *  (salario * 0.2) < 50000           ← agrupación ARITMÉTICA → parsearComparacion la consume
     *
     * Para distinguirlas usamos lookahead: si dentro del paréntesis hay un operador
     * de comparación (=, >, <, >=, <=, !=) a profundidad 0, es lógico. Si no, es aritmético
     * y NO consumimos el '(' aquí — lo dejamos para parsearExpresion().
     */
    private NodoAST parsearTermino() throws ParseException {
        if (esTipo("TOKEN_PARENTESIS_ABRE") && esAgrupacionLogica()) {
            avanzar();                          // consume '('
            NodoAST inner = parsearCondicion(); // condición completa entre paréntesis
            consumir("TOKEN_PARENTESIS_CIERRA");
            return inner;
        }
        return parsearComparacion();
    }

    /**
     * Lookahead: mira los tokens a partir de la posición actual (que debe ser '(')
     * y determina si el paréntesis contiene una condición lógica (comparación) o
     * una expresión aritmética.
     *
     * Regla: si encontramos TOKEN_IGUAL/MAYOR/MENOR/MAYOR_IGUAL/MENOR_IGUAL/DIFERENTE
     * antes del ')' correspondiente (sin bajar de profundidad), es agrupación lógica.
     */
    private boolean esAgrupacionLogica() {
        int depth = 0;
        for (int i = pos; i < tokens.size(); i++) {
            String t = tokens.get(i).getNombre();
            if (t.equals("TOKEN_PARENTESIS_ABRE"))  { depth++; continue; }
            if (t.equals("TOKEN_PARENTESIS_CIERRA")) {
                depth--;
                if (depth == 0) break; // llegamos al cierre del paréntesis actual
                continue;
            }
            // Operador de comparación a profundidad 1 (dentro de nuestros paréntesis)
            if (depth == 1 && esTokenComparacion(t)) return true;
        }
        return false;
    }

    private boolean esTokenComparacion(String tipo) {
        return tipo.equals("TOKEN_IGUAL")       || tipo.equals("TOKEN_MAYOR")  ||
                tipo.equals("TOKEN_MENOR")       || tipo.equals("TOKEN_MAYOR_IGUAL") ||
                tipo.equals("TOKEN_MENOR_IGUAL") || tipo.equals("TOKEN_DIFERENTE");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  COMPARACIÓN  —  soporta expresiones aritméticas y subconsultas
    //
    //  comparacion → expresion OPERADOR expresion
    //  expresion   → PAREN_ABRE consulta PAREN_CIERRA          ← subconsulta
    //              | PAREN_ABRE expr_aritmetica PAREN_CIERRA   ← aritmética
    //              | col (op_arit col|literal)?                ← simple o aritmética sin paréntesis
    //              | literal
    // ══════════════════════════════════════════════════════════════════════════

    private NodoAST parsearComparacion() throws ParseException {
        // Lado izquierdo: expresión (columna, aritmética o subconsulta)
        NodoAST izq = parsearExpresion();

        // Operador de comparación
        String opToken  = tipo();
        String opPython = traducirOperador(opToken);
        avanzar();

        // Lado derecho
        NodoAST der = parsearExpresion();

        return new NodoOperacionBinaria(izq, opPython, der);
    }

    /**
     * Parsea un operando: puede ser una subconsulta entre paréntesis,
     * una expresión aritmética entre paréntesis, una columna con aritmética,
     * o un literal.
     */
    private NodoAST parsearExpresion() throws ParseException {

        // ── Paréntesis: subconsulta  O  agrupación aritmética ─────────────────
        if (esTipo("TOKEN_PARENTESIS_ABRE")) {
            avanzar(); // consume '('

            // ¿Empieza con TRAER? → subconsulta escalar
            if (esTipo("TOKEN_TRAER")) {
                NodoConsulta sub = parsearConsultaInterna();
                consumir("TOKEN_PARENTESIS_CIERRA");
                NodoSubconsulta.resetContador(); // solo reseteamos en la raíz si es necesario
                return new NodoSubconsulta(sub);
            }

            // De lo contrario: expresión aritmética entre paréntesis
            //  (salario * 0.2)  →  NodoExpresionAritmetica
            NodoAST inner = parsearExpresionAritmetica();
            consumir("TOKEN_PARENTESIS_CIERRA");
            return inner;
        }

        // ── Literal numérico o cadena ──────────────────────────────────────────
        if (esTipo("TOKEN_ENTERO") || esTipo("TOKEN_FLOAT")) {
            return parsearLiteral();
        }
        if (esTipo("TOKEN_STRING")) {
            return parsearLiteral();
        }

        // ── Columna (posiblemente seguida de operador aritmético) ──────────────
        String col = consumirNombreColumna();
        NodoAST nodo = new NodoColumna(col);

        // ¿Hay un operador aritmético a continuación sin paréntesis?
        //  salario * 0.2  (sin paréntesis, validamos que esto también funcione)
        if (hayMas() && esOperadorAritmetico(tipo())) {
            String op = tipo(); avanzar();
            NodoAST der = parsearFactorAritmetico();
            nodo = new NodoExpresionAritmetica(nodo, op, der);
        }

        return nodo;
    }

    /**
     * Parsea una expresión aritmética completa dentro de paréntesis.
     * Soporta: col op col,  col op literal,  literal op col,  literal op literal.
     * No soporta anidamiento aritmético profundo (fuera de alcance para este lenguaje).
     */
    private NodoAST parsearExpresionAritmetica() throws ParseException {
        NodoAST izq = parsearFactorAritmetico();

        if (hayMas() && esOperadorAritmetico(tipo())) {
            String op = tipo(); avanzar();
            NodoAST der = parsearFactorAritmetico();
            return new NodoExpresionAritmetica(izq, op, der);
        }
        return izq;
    }

    /** Un factor es una columna o un literal numérico. */
    private NodoAST parsearFactorAritmetico() throws ParseException {
        if (esTipo("TOKEN_ENTERO") || esTipo("TOKEN_FLOAT")) {
            return parsearLiteral();
        }
        // Columna
        String col = consumirNombreColumna();
        return new NodoColumna(col);
    }

    private boolean esOperadorAritmetico(String tipo) {
        return tipo.equals("TOKEN_ASTERISCO") // * también es operador aritmético
                || tipo.equals("TOKEN_MAS")
                || tipo.equals("TOKEN_MENOS")
                || tipo.equals("TOKEN_SLASH");
    }

    /**
     * Parsea una consulta completa de forma recursiva (para subconsultas).
     * Reutiliza la misma gramática que parsear() pero sin el PUNTOCOMA final.
     */
    private NodoConsulta parsearConsultaInterna() throws ParseException {
        consumir("TOKEN_TRAER");
        boolean distinto = consumirSi("TOKEN_DISTINTO");
        NodoSeleccion seleccion = parsearSeleccion(distinto);

        consumir("TOKEN_DESDE");
        String rutaCSV = valorActual();
        consumir("TOKEN_STRING");
        NodoDesde desde = new NodoDesde(rutaCSV);

        NodoDonde donde = null;
        if (hayMas() && esTipo("TOKEN_DONDE")) {
            avanzar();
            NodoAST condicion = parsearCondicion();
            donde = new NodoDonde(condicion);
        }

        // Una subconsulta no tiene ORDENAR, LIMITAR ni GUARDAR
        return new NodoConsulta(seleccion, desde, donde, null, null, null);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  LITERAL
    // ══════════════════════════════════════════════════════════════════════════

    private NodoLiteral parsearLiteral() throws ParseException {
        if (esTipo("TOKEN_ENTERO")) {
            String v = valorActual(); avanzar();
            return new NodoLiteral(v, TipoDato.ENTERO);
        }
        if (esTipo("TOKEN_FLOAT")) {
            String v = valorActual(); avanzar();
            return new NodoLiteral(v, TipoDato.DECIMAL);
        }
        if (esTipo("TOKEN_STRING") || esTipo("TOKEN_ID")) {
            String v = valorActual().replace("\"","").replace("'","");
            avanzar();
            return new NodoLiteral(v, TipoDato.CADENA);
        }
        throw new ParseException(
                "Se esperaba un valor literal (número o texto) pero se encontró: '" + valorActual() + "'");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  UTILIDADES
    // ══════════════════════════════════════════════════════════════════════════

    private boolean hayMas()         { return pos < tokens.size(); }
    private Token   actual()         { return tokens.get(pos); }
    private String  tipo()           { return hayMas() ? actual().getNombre() : "EOF"; }
    private String  valorActual()    { return hayMas() ? actual().getLexema() : ""; }
    private void    avanzar()        { pos++; }
    private boolean esTipo(String t) { return tipo().equals(t); }

    private boolean esAgregacion(String tipo) {
        return tipo.equals("TOKEN_CONTAR")   || tipo.equals("TOKEN_SUMA") ||
                tipo.equals("TOKEN_PROMEDIO") || tipo.equals("TOKEN_MAXIMO") ||
                tipo.equals("TOKEN_MINIMO");
    }

    /** Consume un token que puede ser TOKEN_ID o una palabra reservada usada como nombre de columna */
    private String consumirNombreColumna() throws ParseException {
        if (!hayMas())
            throw new ParseException("Se esperaba un nombre de columna pero se llegó al final de la consulta.");
        // Permitimos TOKEN_ID y también palabras reservadas usadas como nombre de columna
        String val = valorActual();
        avanzar();
        return val;
    }

    private void consumirIdentificador() throws ParseException {
        consumirNombreColumna(); // misma lógica
    }

    /** Consume un token del tipo esperado o lanza ParseException */
    private void consumir(String tipoEsperado) throws ParseException {
        if (!esTipo(tipoEsperado)) {
            String encontrado = hayMas()
                    ? tipo() + " ('" + valorActual() + "')"
                    : "fin de la consulta";
            throw new ParseException(
                    "Error Sintáctico: se esperaba " + tipoEsperado +
                            " pero se encontró: " + encontrado);
        }
        avanzar();
    }

    /** Consume el token solo si es del tipo indicado. Devuelve true si lo consumió. */
    private boolean consumirSi(String tipo) {
        if (esTipo(tipo)) { avanzar(); return true; }
        return false;
    }

    private String traducirOperador(String tokenTipo) throws ParseException {
        return switch (tokenTipo) {
            case "TOKEN_IGUAL"        -> "==";
            case "TOKEN_MAYOR"        -> ">";
            case "TOKEN_MENOR"        -> "<";
            case "TOKEN_MAYOR_IGUAL"  -> ">=";
            case "TOKEN_MENOR_IGUAL"  -> "<=";
            case "TOKEN_DIFERENTE"    -> "!=";
            default -> throw new ParseException(
                    "Error Sintáctico: se esperaba un operador de comparación (=, >, <, >=, <=, !=) " +
                            "pero se encontró: '" + valorActual() + "'");
        };
    }
}