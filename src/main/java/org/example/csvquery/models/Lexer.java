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
                // Sanitizar encabezados: Excel/LibreOffice corrompe "=" a "='"
                // cuando guarda CSVs. Eliminamos apóstrofos finales espurios.
                for (int i = 0; i < encabezados.length; i++) {
                    encabezados[i] = sanitizarEncabezado(encabezados[i]);
                }
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
     * Elimina artefactos que Excel/LibreOffice inyectan al guardar CSVs.
     * El caso más común: "=" se guarda como "='" porque la hoja lo interpreta
     * como inicio de fórmula y escapa el contenido con un apóstrofo prefijo.
     * Aquí lo revertimos quitando apóstrofos al inicio y al final del encabezado.
     */
    private String sanitizarEncabezado(String encabezado) {
        if (encabezado == null) return "";
        // Quitar apóstrofos espurios al inicio (='  →  =)
        String s = encabezado.trim();
        if (s.length() > 1 && s.endsWith("'")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.length() > 1 && s.startsWith("'")) {
            s = s.substring(1);
        }
        return s;
    }


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
                        registrarError(lexemaActual.toString(), estadoSiguiente);
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

                    // Operadores aritméticos simples (+, -, /) no están en la matriz
                    // pero deben emitirse como tokens de un carácter.
                    if (esOperadorAritmeticoSimple(c)) {
                        procesarToken(String.valueOf(c), "q0");
                    } else {
                        // Intentar reprocessar el carácter actual desde q0
                        String colKey2 = obtenerColumna(c);
                        Map<String, String> filaQ0 = matrizTransicion.get("q0");
                        String estadoDesdeQ0 = (filaQ0 != null) ? filaQ0.get(colKey2) : null;
                        if (estadoDesdeQ0 != null) {
                            if (esEstadoDeAceptacionDirecta(estadoDesdeQ0)) {
                                procesarToken(String.valueOf(c), estadoDesdeQ0);
                            } else if (esEstadoDeError(estadoDesdeQ0)) {
                                registrarError(String.valueOf(c), estadoDesdeQ0);
                            } else {
                                lexemaActual.append(c);
                                estadoActual = estadoDesdeQ0;
                            }
                        }
                    }
                }
            }

            // Fin de archivo: emitir token pendiente si lo hay
            if (lexemaActual.length() > 0) {
                if (estadoActual.equals("q22")) {
                    // Cadena que nunca se cerró con "
                    registrarError(lexemaActual.toString(), "qE2");
                } else {
                    procesarToken(lexemaActual.toString().trim(), estadoActual);
                }
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

            // Operadores aritméticos (1100) — para expresiones entre paréntesis
            case "+": id = 1101; nombre = "TOKEN_MAS";    break;
            case "-": id = 1102; nombre = "TOKEN_MENOS";  break;
            case "/": id = 1103; nombre = "TOKEN_SLASH";  break;
            // TOKEN_ASTERISCO ya cubre el '*' como multiplicación además de "todas las columnas"

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

    private void registrarError(String lexema, String estadoError) {
        String nombre;
        int id;

        switch (estadoError) {
            case "qE":
                nombre = "ERROR_OPERADOR_INCOMPLETO";
                id = 5001;
                break;
            case "qE2":
                nombre = "ERROR_CADENA_SIN_CERRAR";
                id = 5002;
                break;
            case "qE3":
                nombre = "ERROR_NUMERO_INVALIDO";
                id = 5003;
                break;
            default:
                nombre = "TOKEN_ERROR_LEXICO";
                id = 5000;
                break;
        }

        pilaErrores.push(new Token(lexema, nombre, id));
    }

    // --- Utils ---
    private String obtenerColumna(char c) {
        if (Character.isWhitespace(c)) return "ws";
        if (Character.isDigit(c)) return String.valueOf(c);
        if (Character.isLetter(c)) return String.valueOf(Character.toLowerCase(c));
        return String.valueOf(c);
    }

    /**
     * Verifica si el carácter es un operador aritmético que no está en la
     * matriz de transición pero debe emitirse como token de un solo carácter.
     */
    private boolean esOperadorAritmeticoSimple(char c) {
        return c == '+' || c == '-' || c == '/';
    }


    private boolean esEstadoPostDelimitador(String estado) {
        return estado.equals("q16") || estado.equals("q20") || estado.equals("q21")
                || estado.equals("q4")  || estado.equals("q7");
    }


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
        System.out.println("\n--- TABLA DE SIMBOLOS ---");
        for (Token t : tablaSimbolos) {
            System.out.println(t.toString());
        }
    }

    public void imprimirPilaErrores() {
        System.out.println("\n--- PILA DE ERRORES LEXICOS ---");
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