package org.example.csvquery.models.ast;

public class NodoOperacionBinaria implements NodoAST {
    private NodoAST izq;
    private String operador; // ">", "<", "==", "Y", "O"
    private NodoAST der;

    public NodoOperacionBinaria(NodoAST izq, String operador, NodoAST der) {
        this.izq = izq;
        this.operador = operador;
        this.der = der;
    }

    public NodoInfo toInfo() {
        NodoInfo n = new NodoInfo("NodoOperacionBinaria", operador, NodoInfo.Categoria.OPERADOR);
        n.hijo(infoDeHijo(izq));
        n.hijo(infoDeHijo(der));
        return n;
    }

    private NodoInfo infoDeHijo(NodoAST nodo) {
        if (nodo instanceof NodoOperacionBinaria op) return op.toInfo();
        if (nodo instanceof NodoColumna col)         return col.toInfo();
        if (nodo instanceof NodoLiteral lit)         return lit.toInfo();
        return new NodoInfo(nodo.getClass().getSimpleName(), "", NodoInfo.Categoria.OPERADOR);
    }

        @Override
    public TipoDato validarSemantica() throws Exception {
        TipoDato tIzq = izq.validarSemantica();
        TipoDato tDer = der.validarSemantica();

        // Regla semántica para comparaciones (>, <, ==)
        if (operador.equals(">") || operador.equals("<") || operador.equals("==")) {
            if (tIzq == tDer || (esNumerico(tIzq) && esNumerico(tDer))) {
                return TipoDato.BOOLEANO; // El resultado de comparar es un True/False
            }
            throw new Exception("Error Semántico: No se puede comparar " + tIzq + " con " + tDer);
        }

        // Regla para operadores lógicos (Y, O)
        if (operador.equals("Y") || operador.equals("O")) {
            if (tIzq == TipoDato.BOOLEANO && tDer == TipoDato.BOOLEANO) {
                return TipoDato.BOOLEANO;
            }
            throw new Exception("Error Semántico: Operadores lógicos requieren booleanos.");
        }

        return TipoDato.ERROR;
    }

    private boolean esNumerico(TipoDato t) {
        return t == TipoDato.ENTERO || t == TipoDato.DECIMAL;
    }

    @Override
    public String generarPython() {
        // Traducimos nuestros operadores a los de Pandas
        String opPython = switch (operador) {
            case "Y" -> "&";
            case "O" -> "|";
            case "==" -> "==";
            default -> operador;
        };
        return "(" + izq.generarPython() + " " + opPython + " " + der.generarPython() + ")";
    }
}