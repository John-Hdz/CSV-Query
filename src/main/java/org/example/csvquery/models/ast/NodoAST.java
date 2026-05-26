package org.example.csvquery.models.ast;

public interface NodoAST
{
    /**
     * Realiza el análisis semántico del nodo.
     * @return El TipoDato que resulta de evaluar este nodo.
     */
    TipoDato validarSemantica() throws Exception;

    /**
     * Genera la porción de código Python/Pandas correspondiente,
     * usando "df" como nombre del dataframe por defecto.
     */
    String generarPython();

    /**
     * Genera código Python usando el nombre de dataframe indicado.
     * Necesario para subconsultas que operan sobre su propio dataframe auxiliar.
     * Por defecto delega a generarPython() (funciona para nodos sin referencia a df).
     */
    default String generarPythonConDf(String nombreDf) {
        return generarPython();
    }


    public abstract NodoInfo toInfo();
}