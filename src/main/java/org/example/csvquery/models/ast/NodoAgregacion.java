package org.example.csvquery.models.ast;

import org.example.csvquery.models.TablaSimbolosCSV;

/**
 * Representa una función de agregación: CONTAR(*), SUMA(col), PROMEDIO(col), etc.
 */
public class NodoAgregacion implements NodoAST {

    private final String funcion;   // TOKEN_CONTAR, TOKEN_SUMA, etc.
    private final String argumento; // nombre de columna o "*"

    public NodoAgregacion(String funcion, String argumento) {
        this.funcion   = funcion;
        this.argumento = argumento;
    }


    public NodoInfo toInfo() {
        NodoInfo n = new NodoInfo("NodoAgregacion",
                nombreLegible() + "(" + argumento + ")",
                NodoInfo.Categoria.AGREGACION);
        n.hijo(new NodoInfo("argumento", argumento, NodoInfo.Categoria.COLUMNA));
        return n;
    }
    @Override
    public TipoDato validarSemantica() throws Exception {
        // CONTAR(*) no necesita validar columna
        if (!argumento.equals("*")) {
            if (!TablaSimbolosCSV.existeColumna(argumento)) {
                throw new Exception(
                    "Error Semántico: La columna '" + argumento + "' usada en " +
                    funcion + "() no existe en el archivo CSV.");
            }
            // SUMA y PROMEDIO solo tienen sentido sobre columnas numéricas
            if (funcion.equals("TOKEN_SUMA") || funcion.equals("TOKEN_PROMEDIO")) {
                TipoDato tipo = TablaSimbolosCSV.obtenerTipoColumna(argumento);
                if (tipo != TipoDato.ENTERO && tipo != TipoDato.DECIMAL) {
                    throw new Exception(
                        "Error Semántico: La función " + nombreLegible() +
                        "() requiere una columna numérica, pero '" +
                        argumento + "' es de tipo " + tipo + ".");
                }
            }
        }
        // El resultado de una agregación siempre es numérico (o entero para CONTAR)
        return funcion.equals("TOKEN_CONTAR") ? TipoDato.ENTERO : TipoDato.DECIMAL;
    }

    @Override
    public String generarPython() {
        return switch (funcion) {
            case "TOKEN_CONTAR"   -> argumento.equals("*")
                                     ? "len(df)"
                                     : "df[\"" + argumento + "\"].count()";
            case "TOKEN_SUMA"     -> "df[\"" + argumento + "\"].sum()";
            case "TOKEN_PROMEDIO" -> "df[\"" + argumento + "\"].mean()";
            case "TOKEN_MAXIMO"   -> "df[\"" + argumento + "\"].max()";
            case "TOKEN_MINIMO"   -> "df[\"" + argumento + "\"].min()";
            default               -> "None";
        };
    }

    private String nombreLegible() {
        return switch (funcion) {
            case "TOKEN_CONTAR"   -> "CONTAR";
            case "TOKEN_SUMA"     -> "SUMA";
            case "TOKEN_PROMEDIO" -> "PROMEDIO";
            case "TOKEN_MAXIMO"   -> "MAXIMO";
            case "TOKEN_MINIMO"   -> "MINIMO";
            default               -> funcion;
        };
    }
}
