package org.example.csvquery.models.ast;

/**
 * Representa una expresión aritmética entre dos operandos.
 * Ejemplo: salario * 0.2  →  NodoExpresionAritmetica(NodoColumna("salario"), "*", NodoLiteral("0.2"))
 *
 * Operadores soportados: +  -  *  /
 *
 * El resultado semántico es numérico (ENTERO o DECIMAL según los operandos).
 * Se genera como Python sin df[]: el llamador (NodoOperacionBinaria) decide
 * si necesita anteponer df[] o no — para columnas directas sí, para expresiones no.
 */
public class NodoExpresionAritmetica implements NodoAST {

    private final NodoAST   izq;
    private final String    operador;   // "+", "-", "*", "/"
    private final NodoAST   der;

    public NodoExpresionAritmetica(NodoAST izq, String operador, NodoAST der) {
        this.izq      = izq;
        this.operador = operador;
        this.der      = der;
    }

    public NodoInfo toInfo() {
        NodoInfo n = new NodoInfo("NodoExpresionAritmetica", operador, NodoInfo.Categoria.OPERADOR);
        n.hijo(infoDeHijo(izq));
        n.hijo(infoDeHijo(der));
        return n;
    }

    private NodoInfo infoDeHijo(NodoAST nodo) {
        if (nodo instanceof NodoExpresionAritmetica ea) return ea.toInfo();
        if (nodo instanceof NodoColumna col)            return col.toInfo();
        if (nodo instanceof NodoLiteral lit)            return lit.toInfo();
        return new NodoInfo(nodo.getClass().getSimpleName(), "", NodoInfo.Categoria.OPERADOR);
    }

    @Override
    public TipoDato validarSemantica() throws Exception {
        TipoDato tIzq = izq.validarSemantica();
        TipoDato tDer = der.validarSemantica();

        // Ambos operandos deben ser numéricos
        boolean izqNum = (tIzq == TipoDato.ENTERO || tIzq == TipoDato.DECIMAL);
        boolean derNum = (tDer == TipoDato.ENTERO || tDer == TipoDato.DECIMAL);

        if (!izqNum || !derNum) {
            throw new Exception(
                    "Error Semántico: El operador aritmético '" + operador +
                            "' solo puede aplicarse a valores numéricos, pero se encontró " +
                            tIzq + " y " + tDer + ".");
        }

        // División siempre produce DECIMAL para evitar la división entera de Python 2
        if (operador.equals("/")) return TipoDato.DECIMAL;

        // Si alguno es DECIMAL, el resultado es DECIMAL
        if (tIzq == TipoDato.DECIMAL || tDer == TipoDato.DECIMAL) return TipoDato.DECIMAL;

        return TipoDato.ENTERO;
    }

    @Override
    public String generarPython() {
        // Traducir nombre de token al símbolo Python correspondiente
        String opPy = switch (operador) {
            case "TOKEN_ASTERISCO", "*" -> "*";
            case "TOKEN_MAS",      "+" -> "+";
            case "TOKEN_MENOS",    "-" -> "-";
            case "TOKEN_SLASH",    "/" -> "/";
            default -> operador;
        };
        String izqPy = wrapColumna(izq);
        String derPy = wrapColumna(der);
        return "(" + izqPy + " " + opPy + " " + derPy + ")";
    }

    /** Si el hijo es una columna simple, la envuelve en df[]. Si ya es una expresión, la usa tal cual. */
    static String wrapColumna(NodoAST nodo) {
        if (nodo instanceof NodoColumna col) return "df[" + col.generarPython() + "]";
        return nodo.generarPython();
    }
}