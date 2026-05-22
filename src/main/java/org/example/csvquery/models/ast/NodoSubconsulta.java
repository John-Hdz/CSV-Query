package org.example.csvquery.models.ast;

/**
 * Representa una subconsulta escalar entre paréntesis.
 *
 * Ejemplo:
 *   edad > (TRAER edad DESDE "datos.csv" DONDE nombre = "Carlos Mendoza")
 *
 * En Python se genera como:
 *   df["edad"] > (pd.read_csv(r"datos.csv")[pd.read_csv(r"datos.csv")["nombre"] == "Carlos Mendoza"]["edad"].iloc[0])
 *
 * Para mayor limpieza, el script genera primero la subconsulta en una variable auxiliar:
 *   _sub0 = pd.read_csv(r"datos.csv")
 *   _sub0 = _sub0[(_sub0["nombre"] == "Carlos Mendoza")]["edad"].iloc[0]
 * y luego usa _sub0 en la condición principal.
 *
 * Semánticamente, devuelve el TipoDato de la columna seleccionada en la subconsulta.
 */
public class NodoSubconsulta implements NodoAST {

    /** Contador estático para generar nombres únicos de variables auxiliares */
    private static int contadorSub = 0;

    private final NodoConsulta consulta;    // La consulta interna completa
    private final String       varAux;      // Nombre de la variable Python auxiliar

    public NodoSubconsulta(NodoConsulta consulta) {
        this.consulta = consulta;
        this.varAux   = "_sub" + (contadorSub++);
    }

    /** Reinicia el contador entre ejecuciones para que los nombres no acumulen */
    public static void resetContador() { contadorSub = 0; }

    public NodoInfo toInfo() {
        NodoInfo n = new NodoInfo("NodoSubconsulta", "( subconsulta )", NodoInfo.Categoria.CONSULTA);
        n.hijo(consulta.toInfo());
        return n;
    }

    @Override
    public TipoDato validarSemantica() throws Exception {
        // Validamos la subconsulta completa.
        // Como NodoConsulta.validarSemantica() llena la TablaSimbolosCSV con el archivo
        // de la subconsulta, necesitamos restaurar la tabla del padre después.
        // La solución simple: la subconsulta valida y devuelve el tipo de su selección.
        consulta.validarSemantica();
        return consulta.getTipoResultado();
    }

    @Override
    public String generarPython() {
        // Generamos el bloque auxiliar que ejecuta la subconsulta y guarda el escalar.
        // El método generarPythonBloque() es el que inserta las líneas "_subN = ..."
        // en el script principal. generarPython() solo devuelve la referencia a la variable.
        return varAux;
    }

    /**
     * Genera las líneas Python que calculan el valor escalar de la subconsulta
     * y lo almacenan en varAux. Debe llamarse ANTES de usar generarPython()
     * en el script principal.
     */
    public String generarPythonBloque() {
        StringBuilder sb = new StringBuilder();
        String df  = varAux + "_df";

        // 1. Leer el CSV de la subconsulta en un dataframe auxiliar
        sb.append(df).append(" = ").append(consulta.getDesde().generarReadCsv()).append("\n");

        // 2. Aplicar DONDE si existe
        NodoDonde donde = consulta.getDonde();
        if (donde != null) {
            // Generamos la condición pero reemplazando "df[" por el dataframe auxiliar
            String condicion = donde.generarCondicionPython(df);
            sb.append(df).append(" = ").append(df).append("[").append(condicion).append("]\n");
        }

        // 3. Seleccionar la columna y tomar el primer valor (.iloc[0])
        String columna = consulta.getSeleccion().getPrimeraColumna();
        sb.append(varAux).append(" = ").append(df)
          .append("[\"").append(columna).append("\"].iloc[0]\n");

        return sb.toString();
    }
}
