package org.example.csvquery.models.ast;

import org.example.csvquery.models.TablaSimbolosCSV;

public class NodoColumna implements NodoAST {
    private String nombreColumna;

    public NodoColumna(String nombreColumna) {
        this.nombreColumna = nombreColumna;
    }

    public NodoInfo toInfo() {
        return new NodoInfo("NodoColumna", nombreColumna, NodoInfo.Categoria.COLUMNA);
    }

        @Override
    public TipoDato validarSemantica() throws Exception {
        // 1. Verificamos si la columna realmente existe en el CSV
        if (!TablaSimbolosCSV.existeColumna(nombreColumna)) {
            throw new Exception("Error Semántico: La columna '" + nombreColumna + "' no existe en el archivo.");
        }

        // 2. Si existe, preguntamos de qué tipo es (para que NodoOperacionBinaria pueda comparar)
        return TablaSimbolosCSV.obtenerTipoColumna(nombreColumna);
    }

    @Override
    public String generarPython() {
        // En tu clase NodoOperacionBinaria, armamos la cadena así:
        // "df[" + izq.generarPython() + "]"
        // Por lo tanto, aquí solo necesitamos devolver el nombre entre comillas.
        return "\"" + nombreColumna + "\"";
    }
}