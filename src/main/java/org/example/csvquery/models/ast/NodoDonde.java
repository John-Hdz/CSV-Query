package org.example.csvquery.models.ast;

public class NodoDonde implements NodoAST {
    private NodoAST expresionLogica; // Aquí va el NodoOperacionBinaria o NodoColumna

    public NodoDonde(NodoAST expresionLogica) {
        this.expresionLogica = expresionLogica;
    }

    public NodoInfo toInfo() {
        NodoInfo n = new NodoInfo("NodoDonde", "condición de filtrado", NodoInfo.Categoria.CLAUSULA);
        n.hijo(toInfoRecursivo(expresionLogica));
        return n;
    }

    private NodoInfo toInfoRecursivo(NodoAST nodo) {
        if (nodo instanceof NodoOperacionBinaria op) return op.toInfo();
        if (nodo instanceof NodoColumna col)         return col.toInfo();
        if (nodo instanceof NodoLiteral lit)         return lit.toInfo();
        return new NodoInfo(nodo.getClass().getSimpleName(), "", NodoInfo.Categoria.OPERADOR);
    }

        @Override
    public TipoDato validarSemantica() throws Exception {
        // Regla de Oro: Lo que esté en el DONDE debe resultar en un Booleano
        TipoDato tipoResultado = expresionLogica.validarSemantica();

        if (tipoResultado != TipoDato.BOOLEANO) {
            throw new Exception("Error Semántico: La condición del DONDE debe ser una expresión lógica (comparación).");
        }

        return TipoDato.VOID;
    }

    @Override
    public String generarPython() {
        // En Pandas, el filtrado se hace como: df = df[ (expresion) ]
        return "df = df[" + expresionLogica.generarPython() + "]\n";
    }
}