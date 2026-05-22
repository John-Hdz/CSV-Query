package org.example.csvquery.models.ast;

import org.example.csvquery.models.TablaSimbolosCSV;

public class NodoDesde implements NodoAST {
    private String rutaArchivo; // ej: "datos.csv"

    public NodoDesde(String rutaArchivo) {
        this.rutaArchivo = rutaArchivo;
    }

    public NodoInfo toInfo() {
        return new NodoInfo("NodoDesde", rutaArchivo, NodoInfo.Categoria.CLAUSULA);
    }

    @Override
    public TipoDato validarSemantica() throws Exception {
        // El NodoDesde es el responsable de llenar la tabla de símbolos
        // quitando las comillas del nombre del archivo si las tiene
        String rutaLimpia = rutaArchivo.replace("\"", "");

        TablaSimbolosCSV.cargarArchivo(rutaLimpia);

        // El "DESDE" no devuelve un valor operable, así que retorna VOID
        return TipoDato.VOID;
    }

    @Override
    public String generarPython() {
        // Usamos r"..." para soportar rutas con barras invertidas (Windows)
        String rutaLimpia = rutaArchivo.replace("\"", "").replace("'", "");
        return "df = pd.read_csv(r\"" + rutaLimpia + "\")\n";
    }

    /**
     * Genera solo la expresión pd.read_csv(...) sin asignarla a "df".
     * Útil para subconsultas que usan su propio dataframe auxiliar.
     */
    public String generarReadCsv() {
        String rutaLimpia = rutaArchivo.replace("\"", "").replace("'", "");
        return "pd.read_csv(r\"" + rutaLimpia + "\")";
    }
}