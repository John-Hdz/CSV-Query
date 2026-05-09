package org.example.csvquery.models.ast;

// ══════════════════════════════════════════════════════════════════════════════
//  NodoGuardar  —  GUARDAR EN "archivo.csv"
// ══════════════════════════════════════════════════════════════════════════════
public class NodoGuardar implements NodoAST {

    private final String rutaSalida;

    public NodoGuardar(String rutaSalida) {
        this.rutaSalida = rutaSalida.replace("\"", "").replace("'", "");
    }

    public NodoInfo toInfo() {
        return new NodoInfo("NodoGuardar", rutaSalida, NodoInfo.Categoria.CLAUSULA);
    }

    @Override
    public TipoDato validarSemantica() throws Exception {
        if (rutaSalida.isBlank()) {
            throw new Exception("Error Semántico: La ruta de GUARDAR EN está vacía.");
        }
        return TipoDato.VOID;
    }

    @Override
    public String generarPython() {
        return "df.to_csv(r\"" + rutaSalida + "\", index=False)\n" +
                "print('Resultado guardado en: " + rutaSalida + "')\n";
    }
}