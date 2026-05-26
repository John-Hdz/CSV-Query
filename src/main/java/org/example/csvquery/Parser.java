package org.example.csvquery;

import org.example.csvquery.models.Token;
import org.example.csvquery.models.ast.*;

import java.util.ArrayList;
import java.util.List;

public class Parser {

    public static class ParseException extends Exception {
        public ParseException(String mensaje) { super(mensaje); }
    }

    private final List<Token> tokens;
    private int pos = 0;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public NodoAST parsear() throws ParseException {

        if (esTipo("TOKEN_INSERTAR")) {
            return parsearInsert();
        }


        if (esTipo("TOKEN_ACTUALIZAR")) {
            return parsearActualizar();
        }

        if (esTipo("TOKEN_ELIMINAR")) {
            return parsearEliminar();
        }

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

    //  SELECCIÓN

    private NodoSeleccion parsearSeleccion(boolean distinto) throws ParseException {

        if (esAgregacion(tipo())) {
            NodoAgregacion agg = parsearAgregacion();
            return new NodoSeleccion(List.of(), false, distinto, agg);
        }

        if (esTipo("TOKEN_ASTERISCO") || esTipo("TOKEN_TODO")) {
            avanzar();
            return new NodoSeleccion(List.of(), true, distinto, null);
        }

        List<String> columnas = new ArrayList<>();
        columnas.add(consumirNombreColumna());

        while (hayMas() && esTipo("TOKEN_COMA")) {
            avanzar();
            columnas.add(consumirNombreColumna());
        }

        return new NodoSeleccion(columnas, false, distinto, null);
    }

    //  AGREGACIÓN
    private NodoAgregacion parsearAgregacion() throws ParseException {
        String funcion = tipo(); avanzar();
        consumir("TOKEN_PARENTESIS_ABRE");

        String arg;
        if (esTipo("TOKEN_ASTERISCO")) { arg = "*"; avanzar(); }
        else                           { arg = consumirNombreColumna(); }

        consumir("TOKEN_PARENTESIS_CIERRA");
        return new NodoAgregacion(funcion, arg);
    }

    //  CONDICIÓN

    private NodoAST parsearCondicion() throws ParseException {
        NodoAST nodo = parsearTermino();

        while (hayMas() && (esTipo("TOKEN_Y") || esTipo("TOKEN_O"))) {
            String operador = esTipo("TOKEN_Y") ? "Y" : "O";
            avanzar();
            nodo = new NodoOperacionBinaria(nodo, operador, parsearTermino());
        }
        return nodo;
    }

    private NodoAST parsearTermino() throws ParseException {
        if (esTipo("TOKEN_PARENTESIS_ABRE") && esAgrupacionLogica()) {
            avanzar();
            NodoAST inner = parsearCondicion();
            consumir("TOKEN_PARENTESIS_CIERRA");
            return inner;
        }
        return parsearComparacion();
    }

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
    //  COMPARACIÓN

    private NodoAST parsearComparacion() throws ParseException {

        NodoAST izq = parsearExpresion();

        String opToken = tipo();

        String opPython = traducirOperador(opToken);

        avanzar();

        NodoAST der;
        if (esTipo("TOKEN_ID")&&
                !valorActual().matches("-?\\d+(\\.\\d+)?")) {

            String valor = valorActual();

            avanzar();

            der = new NodoLiteral(
                    valor,
                    TipoDato.CADENA
            );
        }
        else {

            der = parsearExpresion();
        }

        return new NodoOperacionBinaria(
                izq,
                opPython,
                der
        );
    }

    private NodoAST parsearExpresion() throws ParseException {

        if (esTipo("TOKEN_PARENTESIS_ABRE")) {

            avanzar();
            if (esTipo("TOKEN_TRAER")) {
                NodoConsulta sub = parsearConsultaInterna();
                consumir("TOKEN_PARENTESIS_CIERRA");
                NodoSubconsulta.resetContador();
                return new NodoSubconsulta(sub);
            }
            NodoAST inner = parsearExpresionAritmetica();
            consumir("TOKEN_PARENTESIS_CIERRA");
            return inner;
        }
        if (
                esTipo("TOKEN_ENTERO") ||
                        esTipo("TOKEN_FLOAT")
        ) {
            return parsearLiteral();
        }
        if (
                esTipo("TOKEN_STRING")
        ) {
            return parsearLiteral();
        }
        String col = consumirNombreColumna();
        NodoAST nodo = new NodoColumna(col);
        if (hayMas() && esOperadorAritmetico(tipo())) {
            String op = tipo();
            avanzar();

            NodoAST der = parsearFactorAritmetico();

            nodo = new NodoExpresionAritmetica(
                    nodo,
                    op,
                    der
            );
        }

        return nodo;
    }

    private NodoAST parsearExpresionAritmetica() throws ParseException {
        NodoAST izq = parsearFactorAritmetico();

        if (hayMas() && esOperadorAritmetico(tipo())) {
            String op = tipo(); avanzar();
            NodoAST der = parsearFactorAritmetico();
            return new NodoExpresionAritmetica(izq, op, der);
        }
        return izq;
    }

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

    //INSERT
    private NodoInsertar parsearInsert() throws ParseException {
        consumir("TOKEN_INSERTAR");
        consumir("TOKEN_EN");
        String archivo = valorActual();
        consumir("TOKEN_STRING");
        consumir("TOKEN_PARENTESIS_ABRE");
        List<String> columnas = new ArrayList<>();
        while (!esTipo("TOKEN_PARENTESIS_CIERRA")) {
            columnas.add(valorActual());
            consumirNombreColumna();
            if (esTipo("TOKEN_COMA")) {
                avanzar();
            }
        }
        consumir("TOKEN_PARENTESIS_CIERRA");
        consumir("TOKEN_VALORES");
        consumir("TOKEN_PARENTESIS_ABRE");
        List<String> valores = new ArrayList<>();
        while (!esTipo("TOKEN_PARENTESIS_CIERRA")) {
            valores.add(valorActual().replace("\"",""));
            avanzar();
            if (esTipo("TOKEN_COMA")) {
                avanzar();
            }
        }

        consumir("TOKEN_PARENTESIS_CIERRA");
        if (hayMas() && esTipo("TOKEN_PUNTOCOMA")) {
            avanzar();
        }

        return new NodoInsertar(
                archivo,
                columnas,
                valores
        );
    }

    // ACTUALIZAR
    private NodoActualizar parsearActualizar() throws ParseException {
        consumir("TOKEN_ACTUALIZAR");
        String archivo = valorActual();
        consumir("TOKEN_STRING");
        consumir("TOKEN_VALORES");
        List<String> columnas = new ArrayList<>();
        List<String> valores = new ArrayList<>();
        while (!esTipo("TOKEN_DONDE")) {

            String columna = valorActual();
            consumirNombreColumna();

            consumir("TOKEN_IGUAL");

            String valor = valorActual();
            avanzar();

            columnas.add(columna);
            valores.add(valor);

            if (esTipo("TOKEN_COMA")) {
                avanzar();
            }
        }

        consumir("TOKEN_DONDE");

        NodoAST condicion = parsearCondicion();

        if (hayMas() && esTipo("TOKEN_PUNTOCOMA")) {
            avanzar();
        }

        return new NodoActualizar(
                archivo,
                columnas,
                valores,
                condicion
        );
    }


    //ELIMINAR
    private NodoEliminar parsearEliminar()
            throws ParseException {

        consumir("TOKEN_ELIMINAR");

        String archivo = valorActual();

        consumir("TOKEN_STRING");

        consumir("TOKEN_DONDE");

        NodoAST condicion =
                parsearCondicion();

        if (
                hayMas() &&
                        esTipo("TOKEN_PUNTOCOMA")
        ) {
            avanzar();
        }

        return new NodoEliminar(
                archivo,
                condicion
        );
    }



    //  LITERAL

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

    private String consumirNombreColumna() throws ParseException {
        if (!hayMas())
            throw new ParseException("Se esperaba un nombre de columna pero se llegó al final de la consulta.");
        String val = valorActual();
        avanzar();
        return val;
    }

    private void consumirIdentificador() throws ParseException {
        consumirNombreColumna(); //
    }

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