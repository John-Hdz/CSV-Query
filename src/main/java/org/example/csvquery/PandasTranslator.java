package org.example.csvquery;

import org.example.csvquery.models.Token;

import java.util.List;

/**
 * Traduce la lista de tokens producida por el Lexer
 * a un script Python/pandas ejecutable.
 *
 * Soporta:
 *   TRAER col1, col2 | *
 *   DESDE "archivo.csv"
 *   DONDE col OP valor
 *   ORDENAR POR col ASC|DESC
 *   LIMITAR n
 *   CONTAR(*) / SUMA(col) / PROMEDIO(col) / MAXIMO(col) / MINIMO(col)
 *   DISTINTO
 *   GUARDAR EN "salida.csv"
 */
public class PandasTranslator {

    private final List<Token> tokens;
    private int pos = 0;                    // cursor sobre la lista de tokens

    // Resultado del script generado
    private String rutaCSV        = "";
    private String rutaSalida     = "";     // para GUARDAR EN
    private StringBuilder script  = new StringBuilder();

    public PandasTranslator(List<Token> tokens) {
        this.tokens = tokens;
    }

    /**
     * Genera y devuelve el script Python completo.
     * Lanza IllegalArgumentException si la sintaxis es inválida.
     */
    public String traducir() {
        script.setLength(0);
        pos = 0;

        esperarToken("TOKEN_TRAER");         // TRAER es obligatorio

        boolean distinto     = consumirSi("TOKEN_DISTINTO");
        boolean todoCampos   = false;
        StringBuilder agregacion = new StringBuilder(); // ej: df["edad"].sum()

        // ── Columnas o función de agregación ─────────────────
        StringBuilder columnas = new StringBuilder();   // ["col1","col2"]

        if (esAgregacion(tipo())) {
            agregacion.append(traducirAgregacion());
        } else if (esTipo("TOKEN_ASTERISCO")) {
            todoCampos = true;
            avanzar();
        } else {
            columnas.append("[");
            columnas.append(colComillas(valor()));
            avanzar();
            while (esTipo("TOKEN_COMA")) {
                avanzar();
                columnas.append(", ").append(colComillas(valor()));
                avanzar();
            }
            columnas.append("]");
        }

        // ── DESDE ─────────────────────────────────────────────
        esperarToken("TOKEN_DESDE");
        rutaCSV = valorSinComillas();
        avanzar();

        // ── Inicio del script ─────────────────────────────────
        script.append("import pandas as pd\n");
        script.append("import sys\n\n");
        script.append("df = pd.read_csv(r\"").append(rutaCSV).append("\")\n");

        // ── DONDE (opcional) ──────────────────────────────────
        if (hayMas() && esTipo("TOKEN_DONDE")) {
            avanzar();
            script.append("df = df[").append(traducirCondicion()).append("]\n");
        }

        // ── ORDENAR POR (opcional) ────────────────────────────
        if (hayMas() && esTipo("TOKEN_ORDENAR")) {
            avanzar();
            esperarToken("TOKEN_POR");
            String colOrden = valor(); avanzar();
            boolean asc = true;
            if (hayMas() && esTipo("TOKEN_DESC")) { asc = false; avanzar(); }
            else if (hayMas() && esTipo("TOKEN_ASC")) avanzar();
            script.append("df = df.sort_values(by=")
                  .append(colComillas(colOrden))
                  .append(", ascending=").append(asc ? "True" : "False").append(")\n");
        }

        // ── LIMITAR (opcional) ────────────────────────────────
        if (hayMas() && esTipo("TOKEN_LIMITAR")) {
            avanzar();
            int n = Integer.parseInt(valor()); avanzar();
            script.append("df = df.head(").append(n).append(")\n");
        }

        // ── Proyección / agregación ───────────────────────────
        if (agregacion.length() > 0) {
            // Función de agregación: resultado es un valor escalar
            script.append("resultado = ").append(
                agregacion.toString().replace("df[", "df[")
            ).append("\n");
            script.append("print(resultado)\n");
        } else {
            // Selección de columnas
            if (!todoCampos) {
                script.append("df = df[").append(columnas).append("]\n");
            }
            if (distinto) {
                script.append("df = df.drop_duplicates()\n");
            }

            // ── GUARDAR EN (opcional) ─────────────────────────
            if (hayMas() && esTipo("TOKEN_GUARDAR")) {
                avanzar();
                esperarToken("TOKEN_EN");
                rutaSalida = valorSinComillas(); avanzar();
                script.append("df.to_csv(r\"").append(rutaSalida).append("\", index=False)\n");
                script.append("print('GUARDADO')\n");
                script.append("print(df.to_csv(index=False))\n");
            } else {
                script.append("print(df.to_csv(index=False))\n");
            }
        }

        return script.toString();
    }

    // ══════════════════════════════════════════════════════════
    //  CONDICION  (col OP valor [Y|O col OP valor]*)
    // ══════════════════════════════════════════════════════════

    private String traducirCondicion() {
        StringBuilder sb = new StringBuilder();
        sb.append(traducirComparacion());

        while (hayMas() && (esTipo("TOKEN_Y") || esTipo("TOKEN_O"))) {
            String logico = esTipo("TOKEN_Y") ? " & " : " | ";
            avanzar();
            sb.append(logico).append(traducirComparacion());
        }
        return sb.toString();
    }

    private String traducirComparacion() {
        String col = valor(); avanzar();
        String op  = traducirOperador(tipo()); avanzar();
        String val = valor(); avanzar();

        boolean esNumero = val.matches("-?\\d+(\\.\\d+)?");
        String valPy = esNumero ? val : ("\"" + val.replace("\"","").replace("'","") + "\"");

        return "(df[\"" + col + "\"] " + op + " " + valPy + ")";
    }

    private String traducirOperador(String tokenTipo) {
        return switch (tokenTipo) {
            case "TOKEN_IGUAL"        -> "==";
            case "TOKEN_MAYOR"        -> ">";
            case "TOKEN_MENOR"        -> "<";
            case "TOKEN_MAYOR_IGUAL"  -> ">=";
            case "TOKEN_MENOR_IGUAL"  -> "<=";
            case "TOKEN_DIFERENTE"    -> "!=";
            default -> throw new IllegalArgumentException("Operador desconocido: " + tokenTipo);
        };
    }

    // ══════════════════════════════════════════════════════════
    //  AGREGACIONES  CONTAR / SUMA / PROMEDIO / MAXIMO / MINIMO
    // ══════════════════════════════════════════════════════════

    private boolean esAgregacion(String tipo) {
        return tipo.equals("TOKEN_CONTAR")   || tipo.equals("TOKEN_SUMA") ||
               tipo.equals("TOKEN_PROMEDIO") || tipo.equals("TOKEN_MAXIMO") ||
               tipo.equals("TOKEN_MINIMO");
    }

    private String traducirAgregacion() {
        String funcion = tipo(); avanzar();
        // Esperar '('
        esperarToken("TOKEN_PARENTESIS_ABRE");
        String arg = valor(); avanzar();   // columna o *
        esperarToken("TOKEN_PARENTESIS_CIERRA");

        return switch (funcion) {
            case "TOKEN_CONTAR"   -> arg.equals("*")
                ? "len(df)"
                : "df[\"" + arg + "\"].count()";
            case "TOKEN_SUMA"     -> "df[\"" + arg + "\"].sum()";
            case "TOKEN_PROMEDIO" -> "df[\"" + arg + "\"].mean()";
            case "TOKEN_MAXIMO"   -> "df[\"" + arg + "\"].max()";
            case "TOKEN_MINIMO"   -> "df[\"" + arg + "\"].min()";
            default -> throw new IllegalArgumentException("Función desconocida: " + funcion);
        };
    }

    // ══════════════════════════════════════════════════════════
    //  UTILIDADES DE NAVEGACIÓN
    // ══════════════════════════════════════════════════════════

    private boolean hayMas()               { return pos < tokens.size(); }
    private Token   actual()               { return tokens.get(pos); }
    private String  tipo()                 { return hayMas() ? actual().getNombre() : ""; }
    private String  valor()                { return hayMas() ? actual().getLexema() : ""; }
    private void    avanzar()              { pos++; }

    private boolean esTipo(String t)       { return tipo().equals(t); }

    private boolean consumirSi(String t) {
        if (esTipo(t)) { avanzar(); return true; }
        return false;
    }

    private void esperarToken(String t) {
        if (!esTipo(t))
            throw new IllegalArgumentException(
                "Se esperaba " + t + " pero se encontró: " + tipo() + " ('" + valor() + "')");
        avanzar();
    }

    private String valorSinComillas() {
        return valor().replace("\"", "").replace("'", "");
    }

    private String colComillas(String col) {
        return "\"" + col.replace("\"","") + "\"";
    }

    public String getRutaCSV()    { return rutaCSV; }
    public String getRutaSalida() { return rutaSalida; }
}
