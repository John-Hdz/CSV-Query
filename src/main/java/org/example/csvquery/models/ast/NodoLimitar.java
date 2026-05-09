package org.example.csvquery.models.ast;

// ══════════════════════════════════════════════════════════════════════════════
//  NodoLimitar  —  LIMITAR n
// ══════════════════════════════════════════════════════════════════════════════
public class NodoLimitar implements NodoAST {

    private final int cantidad;

    public NodoLimitar(int cantidad) {
        this.cantidad = cantidad;
    }

    public NodoInfo toInfo() {
        return new NodoInfo("NodoLimitar", String.valueOf(cantidad), NodoInfo.Categoria.CLAUSULA);
    }

    @Override
    public TipoDato validarSemantica() throws Exception {
        if (cantidad <= 0) {
            throw new Exception(
                    "Error Semántico: El valor de LIMITAR debe ser un entero positivo, " +
                            "pero se recibió: " + cantidad);
        }
        return TipoDato.VOID;
    }

    @Override
    public String generarPython() {
        return "df = df.head(" + cantidad + ")\n";
    }
}
