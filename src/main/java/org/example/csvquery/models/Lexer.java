package org.example.csvquery.models;

import com.opencsv.CSVReader;

import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class Lexer {
    // Estructuras de almacenamiento
    private List<Token> tablaSimbolos = new ArrayList<>();
    private Stack<Token> pilaErrores = new Stack<>();

    // Matriz de transiciones: Map<EstadoActual, Map<Caracter, EstadoSiguiente>>
    private Map<String, Map<String, String>> matrizTransicion = new HashMap<>();
    private List<String> columnasAlfabeto = new ArrayList<>();

    public Lexer(String rutaCsvAutomata) {
        cargarAutómata(rutaCsvAutomata);
    }

    // 1. Cargar la matriz de transición desde el CSV
    private void cargarAutómata(String rutaCsv) {
        try (CSVReader reader = new CSVReader(new FileReader(rutaCsv))) {
            String[] encabezados = reader.readNext();
            if (encabezados != null) {
                // Guardar los encabezados (el alfabeto) ignorando la primera columna ("Estado")
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

    // 2. Método para leer el archivo de texto y analizar tokens
    public void analizarArchivo(String rutaCodigoFuente) {
        try (FileReader fr = new FileReader(rutaCodigoFuente)) {
            int caracter;
            String estadoActual = "q0";
            StringBuilder lexemaActual = new StringBuilder();

            while ((caracter = fr.read()) != -1) {
                char c = (char) caracter;
                String colKey = obtenerColumna(c);

                // Obtener el siguiente estado
                Map<String, String> filaEstado = matrizTransicion.get(estadoActual);
                String estadoSiguiente = (filaEstado != null) ? filaEstado.get(colKey) : null;

                if (estadoSiguiente != null) {
                    lexemaActual.append(c);
                    estadoActual = estadoSiguiente;

                    // Comprobar si llegamos a un estado de aceptación que requiera corte (ej: post delimitador)
                    // En tu tabla, q16, q20, q21, q23 suelen ser de aceptación de tokens completos.
                    if (esEstadoDeAceptacion(estadoActual)) {
                        procesarToken(lexemaActual.toString().trim(), estadoActual);
                        lexemaActual.setLength(0); // Limpiar buffer
                        estadoActual = "q0";       // Reiniciar autómata
                    } else if (esEstadoDeError(estadoActual)) {
                        registrarError(lexemaActual.toString());
                        lexemaActual.setLength(0);
                        estadoActual = "q0";
                    }
                } else {
                    // Si no hay transición, forzamos el corte del token actual (si lo hay) y reiniciamos
                    if (lexemaActual.length() > 0) {
                        procesarToken(lexemaActual.toString().trim(), estadoActual);
                        lexemaActual.setLength(0);
                    }
                    estadoActual = "q0";
                }
            }
        } catch (IOException e) {
            System.err.println("Error al leer el código fuente: " + e.getMessage());
        }
    }

    // 3. El Switch principal que asigna el ID y lo manda a la Tabla de Símbolos
    private void procesarToken(String lexema, String estadoFinal) {
        if (lexema.isEmpty()) return;

        int id = 4001; // ID por defecto (ej. Identificador)
        String nombre = "TOKEN_ID";

        // Mapeo por switch case basado en el lexema
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

            // Valores dinámicos (Caen en default y se validan por estado)
            default:
                if (estadoFinal.equals("q20")) {
                    id = 4002; nombre = "TOKEN_ENTERO";
                } else if (estadoFinal.equals("q21")) {
                    id = 4003; nombre = "TOKEN_FLOAT";
                } else if (estadoFinal.equals("q23")) {
                    id = 4004; nombre = "TOKEN_STRING";
                }
                break;
        }

        // Agregar a la tabla de símbolos
        tablaSimbolos.add(new Token(lexema, nombre, id));
    }

    // 4. Manejo de Errores Léxicos
    private void registrarError(String lexema) {
        Token error = new Token(lexema, "TOKEN_ERROR_LEXICO", 5001);
        pilaErrores.push(error);
    }

    // --- Utilidades ---
    private String obtenerColumna(char c) {
        if (Character.isWhitespace(c)) return "ws";
        if (Character.isDigit(c)) return String.valueOf(c);
        if (Character.isLetter(c)) return String.valueOf(Character.toLowerCase(c));
        return String.valueOf(c); // Para símbolos como = > < ! " ; * ( )
    }

    private boolean esEstadoDeAceptacion(String estado) {
        // Modifica esto según tu diseño. Estos estados representan un token finalizado.
        return estado.equals("q3") || estado.equals("q4") || estado.equals("q6") ||
                estado.equals("q9") || estado.equals("q10") || estado.equals("q16") ||
                estado.equals("q20") || estado.equals("q21") || estado.equals("q23");
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
