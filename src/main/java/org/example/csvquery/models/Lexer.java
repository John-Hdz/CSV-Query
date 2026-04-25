package org.example.csvquery.models;

import com.opencsv.CSVReader;

import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class Lexer {

    private List<Token> tablaSimbolos = new ArrayList<>();
    private Stack<Token> pilaErrores = new Stack<>();

    // Map<EstadoActual, Map<Caracter, EstadoSiguiente>>
    private Map<String, Map<String, String>> matrizTransicion = new HashMap<>();
    private List<String> columnasAlfabeto = new ArrayList<>();

    public Lexer(String rutaCsvAutomata) {
        cargarAutómata(rutaCsvAutomata);
    }

    // Cargar la matriz de transición desde el CSV
    private void cargarAutómata(String rutaCsv) {
        try (CSVReader reader = new CSVReader(new FileReader(rutaCsv))) {
            String[] encabezados = reader.readNext();
            if (encabezados != null) {
                columnasAlfabeto.addAll(Arrays.asList(encabezados).subList(1, encabezados.length));
            }

            String[] fila;
            while ((fila = reader.readNext()) != null) {
                String estado = fila[0].split(" ")[0]; // Extrae "q0" de "q0 (inicial)"
                Map<String, String> transiciones = new HashMap<>();

                for (int i = 1; i < fila.length; i++) {
                    String estadoDestino = fila[i].trim();
                    if (!estadoDestino.equals("-")) {
                        transiciones.put(encabezados[i], estadoDestino.split(" ")[0]);
                    }
                }
                matrizTransicion.put(estado, transiciones);
            }
            System.out.println("Autómata cargado exitosamente. Estados: " + matrizTransicion.size());
        } catch (Exception e) {
            System.err.println("Error al cargar el CSV del autómata: " + e.getMessage());
        }
    }

    /**
     * Lee el archivo de código fuente carácter a carácter.
     *
     * CORRECCIÓN PRINCIPAL: Los estados "post-delimitador" del autómata (q3, q4, q6, q7,
     * q16, q20, q21) se alcanzan consumiendo un delimitador que NO debe formar parte del
     * lexema actual. La solución es un diseño de "lookahead":
     *
     *   1. Antes de appendear el carácter al lexema, se calcula el estado siguiente.
     *   2. Si ese estado siguiente ES de aceptación-con-delimitador (delim-accept),
     *      se emite el token con el lexema ACTUAL (sin incluir el delimitador),
     *      y el delimitador se "regresa" para ser procesado de nuevo en q0.
     *   3. Si el estado es de aceptación directa (sin consumir delimitador extra),
     *      el carácter SÍ forma parte del token (ej. q9=!=, q10=coma, q11=;, etc.).
     */
    public void analizarArchivo(String rutaCodigoFuente) {
        try (FileReader fr = new FileReader(rutaCodigoFuente)) {
            int caracter;
            String estadoActual = "q0";
            StringBuilder lexemaActual = new StringBuilder();

            while ((caracter = fr.read()) != -1) {
                char c = (char) caracter;
                String colKey = obtenerColumna(c);

                Map<String, String> filaEstado = matrizTransicion.get(estadoActual);
                String estadoSiguiente = (filaEstado != null) ? filaEstado.get(colKey) : null;

                if (estadoSiguiente != null) {

                    // BUG FIX: Los estados post-delimitador significan que el carácter 'c'
                    // es un delimitador que cierra el token anterior, NO parte del lexema.
                    // En estos casos emitimos el token sin incluir 'c', y reprocessamos
                    // 'c' desde q0 en la siguiente iteración.
                    if (esEstadoPostDelimitador(estadoSiguiente)) {
                        // Emitir el token acumulado hasta ahora
                        if (lexemaActual.length() > 0) {
                            procesarToken(lexemaActual.toString().trim(), estadoSiguiente);
                            lexemaActual.setLength(0);
                        }
                        estadoActual = "q0";

                        // Reprocessar el delimitador desde q0
                        // (puede ser ws→ignorar, o coma/;/símbolo→su propio token)
                        String colKey2 = obtenerColumna(c);
                        Map<String, String> filaQ0 = matrizTransicion.get("q0");
                        String estadoDesdeQ0 = (filaQ0 != null) ? filaQ0.get(colKey2) : null;

                        if (estadoDesdeQ0 != null) {
                            // El delimitador es un token por sí mismo (ej: ',', ';', '*')
                            if (esEstadoDeAceptacionDirecta(estadoDesdeQ0)) {
                                // Tokens de un solo carácter: emitir inmediatamente
                                procesarToken(String.valueOf(c), estadoDesdeQ0);
                                estadoActual = "q0";
                            } else {
                                // Inicio de otro token (ej: letra, dígito, operador)
                                lexemaActual.append(c);
                                estadoActual = estadoDesdeQ0;
                            }
                        }
                        // Si estadoDesdeQ0 es null (ej: whitespace sin transición definida
                        // o no existe en la matriz), simplemente lo ignoramos → estadoActual = q0

                    } else if (esEstadoDeAceptacionDirecta(estadoSiguiente)) {
                        // BUG FIX: Tokens de un solo carácter que se aceptan inmediatamente
                        // (coma, punto y coma, paréntesis, !=, >=, <=).
                        // El carácter SÍ forma parte del lexema.
                        lexemaActual.append(c);
                        procesarToken(lexemaActual.toString().trim(), estadoSiguiente);
                        lexemaActual.setLength(0);
                        estadoActual = "q0";

                    } else if (esEstadoDeError(estadoSiguiente)) {
                        lexemaActual.append(c);
                        registrarError(lexemaActual.toString());
                        lexemaActual.setLength(0);
                        estadoActual = "q0";

                    } else {
                        // Estado intermedio: acumular carácter y continuar
                        lexemaActual.append(c);
                        estadoActual = estadoSiguiente;
                    }

                } else {
                    // Sin transición desde el estado actual con este carácter.
                    // Emitir lo acumulado (si hay) y reiniciar.
                    if (lexemaActual.length() > 0) {
                        procesarToken(lexemaActual.toString().trim(), estadoActual);
                        lexemaActual.setLength(0);
                    }
                    estadoActual = "q0";

                    // Intentar reprocessar el carácter actual desde q0
                    String colKey2 = obtenerColumna(c);
                    Map<String, String> filaQ0 = matrizTransicion.get("q0");
                    String estadoDesdeQ0 = (filaQ0 != null) ? filaQ0.get(colKey2) : null;
                    if (estadoDesdeQ0 != null) {
                        if (esEstadoDeAceptacionDirecta(estadoDesdeQ0)) {
                            procesarToken(String.valueOf(c), estadoDesdeQ0);
                        } else if (!esEstadoDeError(estadoDesdeQ0)) {
                            lexemaActual.append(c);
                            estadoActual = estadoDesdeQ0;
                        }
                    }
                }
            }

            // Fin de archivo: emitir token pendiente si lo hay
            if (lexemaActual.length() > 0) {
                procesarToken(lexemaActual.toString().trim(), estadoActual);
            }

        } catch (IOException e) {
            System.err.println("Error al leer el código fuente: " + e.getMessage());
        }
    }

    // Switch principal que asigna el ID y lo manda a la Tabla de Símbolos
    private void procesarToken(String lexema, String estadoFinal) {
        if (lexema.isEmpty()) return;

        int id = 4001;
        String nombre = "TOKEN_ID";

        switch (lexema.toUpperCase()) {
            // Palabras Reservadas (2000)
            case "TRAER":    id = 2001; nombre = "TOKEN_TRAER"; break;
            case "DESDE":    id = 2002; nombre = "TOKEN_DESDE"; break;
            case "DONDE":    id = 2003; nombre = "TOKEN_DONDE"; break;
            case "ORDENAR":  id = 2004; nombre = "TOKEN_ORDENAR"; break;
            case "POR":      id = 2005; nombre = "TOKEN_POR"; break;
            case "Y":        id = 2006; nombre = "TOKEN_Y"; break;
            case "O":        id = 2007; nombre = "TOKEN_O"; break;
            case "LIMITAR":  id = 2008; nombre = "TOKEN_LIMITAR"; break;
            case "DISTINTO": id = 2009; nombre = "TOKEN_DISTINTO"; break;
            case "ASC":      id = 2010; nombre = "TOKEN_ASC"; break;
            case "DESC":     id = 2011; nombre = "TOKEN_DESC"; break;
            case "COMO":     id = 2012; nombre = "TOKEN_COMO"; break;
            case "CONTAR":   id = 2013; nombre = "TOKEN_CONTAR"; break;
            case "PROMEDIO": id = 2014; nombre = "TOKEN_PROMEDIO"; break;
            case "SUMA":     id = 2015; nombre = "TOKEN_SUMA"; break;
            case "MAXIMO":   id = 2016; nombre = "TOKEN_MAXIMO"; break;
            case "MINIMO":   id = 2017; nombre = "TOKEN_MINIMO"; break;
            case "TODO":     id = 2018; nombre = "TOKEN_TODO"; break;
            case "GUARDAR":  id = 2019; nombre = "TOKEN_GUARDAR"; break;
            case "EN":       id = 2020; nombre = "TOKEN_EN"; break;

            // Operadores (1000)
            case "=":  id = 1001; nombre = "TOKEN_IGUAL"; break;
            case ">":  id = 1002; nombre = "TOKEN_MAYOR"; break;
            case "<":  id = 1003; nombre = "TOKEN_MENOR"; break;
            case ">=": id = 1004; nombre = "TOKEN_MAYOR_IGUAL"; break;
            case "<=": id = 1005; nombre = "TOKEN_MENOR_IGUAL"; break;
            case "!=": id = 1006; nombre = "TOKEN_DIFERENTE"; break;

            // Símbolos Especiales (3000)
            case ",": id = 3001; nombre = "TOKEN_COMA"; break;
            case ";": id = 3002; nombre = "TOKEN_PUNTOCOMA"; break;
            case "*": id = 3003; nombre = "TOKEN_ASTERISCO"; break;
            case "(": id = 3004; nombre = "TOKEN_PARENTESIS_ABRE"; break;
            case ")": id = 3005; nombre = "TOKEN_PARENTESIS_CIERRA"; break;

            // Valores dinámicos validados por estado final
            default:
                if (estadoFinal.equals("q17") || estadoFinal.equals("q20")) {
                    id = 4002; nombre = "TOKEN_ENTERO";
                } else if (estadoFinal.equals("q19") || estadoFinal.equals("q21")) {
                    id = 4003; nombre = "TOKEN_FLOAT";
                } else if (estadoFinal.equals("q23")) {
                    id = 4004; nombre = "TOKEN_STRING";
                }
                break;
        }

        tablaSimbolos.add(new Token(lexema, nombre, id));
    }

    private void registrarError(String lexema) {
        Token error = new Token(lexema, "TOKEN_ERROR_LEXICO", 5001);
        pilaErrores.push(error);
    }

    // --- Utils ---
    private String obtenerColumna(char c) {
        if (Character.isWhitespace(c)) return "ws";
        if (Character.isDigit(c)) return String.valueOf(c);
        if (Character.isLetter(c)) return String.valueOf(Character.toLowerCase(c));
        return String.valueOf(c);
    }

    /**
     * Estados "post-delimitador": el autómata llega a estos estados habiendo consumido
     * un carácter delimitador que cierra el token ANTERIOR. El delimitador NO debe
     * incluirse en el lexema del token que se va a emitir.
     *
     * q16 = identificador terminado por ws o delimitador
     * q20 = entero terminado por ws o delimitador
     * q21 = float terminado por ws o delimitador
     * q4  = '>' solo (sin '='), terminado al leer el siguiente char
     * q7  = '<' solo (sin '='), terminado al leer el siguiente char
     */
    private boolean esEstadoPostDelimitador(String estado) {
        return estado.equals("q16") || estado.equals("q20") || estado.equals("q21")
                || estado.equals("q4")  || estado.equals("q7");
    }

    /**
     * Estados de aceptación directa: el carácter que provocó la transición SÍ pertenece
     * al token (es parte del lexema) y el token queda completo en ese instante.
     *
     * q1  = '='
     * q3  = '>='
     * q6  = '<='
     * q9  = '!='
     * q10 = ','
     * q11 = ';'
     * q12 = '*'
     * q13 = '('
     * q14 = ')'
     * q23 = cadena (la " de cierre la incluimos en el lexema para poder stripearla)
     */
    private boolean esEstadoDeAceptacionDirecta(String estado) {
        return estado.equals("q1")  || estado.equals("q3")  || estado.equals("q6")  ||
                estado.equals("q9")  || estado.equals("q10") || estado.equals("q11") ||
                estado.equals("q12") || estado.equals("q13") || estado.equals("q14") ||
                estado.equals("q23");
    }

    private boolean esEstadoDeError(String estado) {
        return estado.equals("qE") || estado.equals("qE2") || estado.equals("qE3");
    }

    // Métodos para imprimir resultados
    public void imprimirTablaSimbolos() {
        System.out.println("\n--- TABLA DE SÍMBOLOS ---");
        for (Token t : tablaSimbolos) {
            System.out.println(t.toString());
        }
    }

    public void imprimirPilaErrores() {
        System.out.println("\n--- PILA DE ERRORES LÉXICOS ---");
        while (!pilaErrores.isEmpty()) {
            System.out.println(pilaErrores.pop().toString());
        }
    }

    public List<Token> getTablaSimbolos() {
        return tablaSimbolos;
    }

    public List<Token> getPilaErrores() {
        return new ArrayList<>(pilaErrores);
    }
}