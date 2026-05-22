package org.example.csvquery.models.ast;

import org.example.csvquery.models.TablaSimbolosCSV;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Nodo de selección de columnas.
 * Ahora también soporta funciones de agregación (CONTAR, SUMA, etc.)
 * y la cláusula DISTINTO.
 */
public class NodoSeleccion implements NodoAST {

    private final List<String>    columnas;    // Nombres de columnas explícitas
    private final boolean         todo;        // true si se usó * o TODO
    private final boolean         distinto;    // true si se usó DISTINTO
    private final NodoAgregacion  agregacion;  // null si no hay función de agregación

    public NodoSeleccion(List<String> columnas, boolean todo,
                         boolean distinto, NodoAgregacion agregacion) {
        this.columnas   = columnas;
        this.todo       = todo;
        this.distinto   = distinto;
        this.agregacion = agregacion;
    }


    public NodoInfo toInfo() {
        String det = todo ? "todas las columnas (*)"
                : (agregacion != null ? "función de agregación"
                : String.join(", ", columnas));
        NodoInfo n = new NodoInfo("NodoSeleccion",
                (distinto ? "DISTINTO  " : "") + det,
                NodoInfo.Categoria.CLAUSULA);

        if (agregacion != null) {
            n.hijo(agregacion.toInfo());
        } else {
            for (String col : columnas)
                n.hijo(new NodoInfo("NodoColumna", col, NodoInfo.Categoria.COLUMNA));
        }
        return n;
    }
    @Override
    public TipoDato validarSemantica() throws Exception {
        // Si hay agregación, delegamos su validación
        if (agregacion != null) {
            return agregacion.validarSemantica();
        }

        // Si no es *, validamos que cada columna exista
        if (!todo) {
            for (String col : columnas) {
                if (!TablaSimbolosCSV.existeColumna(col)) {
                    throw new Exception(
                            "Error Semántico: La columna '" + col +
                                    "' no existe en el archivo CSV.");
                }
            }
        }
        return TipoDato.VOID;
    }

    @Override
    public String generarPython() {
        // Caso 1: función de agregación — resultado es un escalar
        if (agregacion != null) {
            return "resultado = " + agregacion.generarPython() + "\n" +
                    "print(resultado)\n";
        }

        StringBuilder sb = new StringBuilder();

        // Caso 2: columnas explícitas
        if (!todo) {
            String colsFormateadas = columnas.stream()
                    .map(c -> "\"" + c + "\"")
                    .collect(Collectors.joining(", "));
            sb.append("df = df[[").append(colsFormateadas).append("]]\n");
        }
        // Caso 3: * → no filtramos columnas

        // DISTINTO elimina duplicados
        if (distinto) {
            sb.append("df = df.drop_duplicates()\n");
        }

        return sb.toString();
    }

    public boolean tieneAgregacion() { return agregacion != null; }

    /**
     * Devuelve el nombre de la primera columna seleccionada.
     * Usado por NodoSubconsulta para saber qué columna extraer (.iloc[0]).
     */
    public String getPrimeraColumna() {
        if (!columnas.isEmpty()) return columnas.get(0);
        throw new IllegalStateException("La subconsulta debe seleccionar una columna explícita (no * ni agregación).");
    }

    /**
     * Devuelve el TipoDato de la primera columna seleccionada.
     * Válido solo después de validarSemantica().
     */
    public TipoDato getTipoResultado() {
        if (!columnas.isEmpty()) {
            return TablaSimbolosCSV.obtenerTipoColumna(columnas.get(0));
        }
        return TipoDato.ERROR;
    }
}