package org.example.csvquery.models.ast;

public class NodoLiteral implements NodoAST {
    private String valor;
    private TipoDato tipo;

    public NodoLiteral(String valor, TipoDato tipo) {
        this.valor = valor;
        this.tipo = tipo;
    }

    public NodoInfo toInfo() {
        return new NodoInfo("NodoLiteral", valor + "  (" + tipo + ")", NodoInfo.Categoria.LITERAL);
    }

        @Override
    public TipoDato validarSemantica() {
        // Como es un valor fijo (ej. 20), su tipo es absoluto y nunca falla.
        return this.tipo;
    }

    @Override
    public String generarPython() {
        // Si el usuario escribió un texto, Python necesita que tenga comillas.
        // Si es un número, se escribe tal cual.
        if (tipo == TipoDato.CADENA) {
            return "\"" + valor + "\"";
        }
        return valor;
    }
}