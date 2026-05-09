package org.example.csvquery.models.ast;

import org.example.csvquery.models.TablaSimbolosCSV;

// ══════════════════════════════════════════════════════════════════════════════
//  NodoOrdenar  —  ORDENAR POR col ASC|DESC
// ══════════════════════════════════════════════════════════════════════════════
public class NodoOrdenar implements NodoAST {

    private final String  columna;
    private final boolean ascendente;

    public NodoOrdenar(String columna, boolean ascendente) {
        this.columna     = columna;
        this.ascendente  = ascendente;
    }

    public NodoInfo toInfo() {
        NodoInfo n = new NodoInfo("NodoOrdenar",
                columna + "  " + (ascendente ? "ASC" : "DESC"),
                NodoInfo.Categoria.CLAUSULA);
        n.hijo(new NodoInfo("NodoColumna", columna, NodoInfo.Categoria.COLUMNA));
        return n;
    }

    @Override
    public TipoDato validarSemantica() throws Exception {
        if (!TablaSimbolosCSV.existeColumna(columna)) {
            throw new Exception(
                    "Error Semántico: La columna '" + columna +
                            "' usada en ORDENAR POR no existe en el archivo CSV.");
        }
        return TipoDato.VOID;
    }

    @Override
    public String generarPython() {
        return "df = df.sort_values(by=\"" + columna + "\", ascending=" +
                (ascendente ? "True" : "False") + ")\n";
    }
}
