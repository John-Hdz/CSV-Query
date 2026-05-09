package org.example.csvquery.models.ast;

public interface NodoAST
{
    /**
     * Realiza el análisis semántico del nodo.
     * @return El TipoDato que resulta de evaluar este nodo.
     */
    TipoDato validarSemantica() throws Exception;

    /**
     * Genera la porción de código Python/Pandas correspondiente.
     */
    String generarPython();
}
