package org.example.csvquery.models.ast;

/**
 * Nodo raíz del AST. Orquesta la validación semántica y la generación
 * del script Python completo en el orden correcto.
 *
 * Orden de validación semántica (crítico):
 *   1. NodoDesde  → carga la TablaSimbolosCSV con las columnas reales del archivo
 *   2. NodoSeleccion → valida que las columnas pedidas existan
 *   3. NodoDonde  → valida tipos en la condición
 *   4. NodoOrdenar → valida que la columna de orden exista
 *   5. NodoLimitar → valida que el límite sea positivo
 *   6. NodoGuardar → valida que la ruta no esté vacía
 *
 * Orden de generación del script Python:
 *   import → read_csv → filtro (DONDE) → orden → límite → proyección → print/guardar
 */
public class NodoConsulta implements NodoAST {

    private final NodoSeleccion seleccion;
    private final NodoDesde     desde;
    private final NodoDonde     donde;      // null si no hay DONDE
    private final NodoOrdenar   ordenar;    // null si no hay ORDENAR POR
    private final NodoLimitar   limitar;    // null si no hay LIMITAR
    private final NodoGuardar   guardar;    // null si no hay GUARDAR EN

    public NodoConsulta(NodoSeleccion seleccion, NodoDesde desde,
                        NodoDonde donde, NodoOrdenar ordenar,
                        NodoLimitar limitar, NodoGuardar guardar) {
        this.seleccion = seleccion;
        this.desde     = desde;
        this.donde     = donde;
        this.ordenar   = ordenar;
        this.limitar   = limitar;
        this.guardar   = guardar;
    }


    // ── Descripción para la UI del AST ───────────────────────────────────────

    public NodoInfo toInfo() {
        NodoInfo raiz = new NodoInfo("NodoConsulta", "consulta completa", NodoInfo.Categoria.CONSULTA);

        raiz.hijo(seleccion.toInfo());
        raiz.hijo(desde.toInfo());

        if (donde  != null) raiz.hijo(donde.toInfo());
        else raiz.hijo(new NodoInfo("NodoDonde",  "—  no definido", NodoInfo.Categoria.CLAUSULA, false));

        if (ordenar != null) raiz.hijo(ordenar.toInfo());
        else raiz.hijo(new NodoInfo("NodoOrdenar","—  no definido", NodoInfo.Categoria.CLAUSULA, false));

        if (limitar != null) raiz.hijo(limitar.toInfo());
        else raiz.hijo(new NodoInfo("NodoLimitar","—  no definido", NodoInfo.Categoria.CLAUSULA, false));

        if (guardar != null) raiz.hijo(guardar.toInfo());
        else raiz.hijo(new NodoInfo("NodoGuardar","—  no definido", NodoInfo.Categoria.CLAUSULA, false));

        return raiz;
    }
    // ── Validación semántica ───────────────────────────────────────────────────

    @Override
    public TipoDato validarSemantica() throws Exception {

        // 1. DESDE primero → llena la TablaSimbolosCSV
        desde.validarSemantica();

        // 2. Selección (columnas o agregación)
        seleccion.validarSemantica();

        // 3. Condición DONDE
        if (donde != null) donde.validarSemantica();

        // 4. Orden
        if (ordenar != null) ordenar.validarSemantica();

        // 5. Límite
        if (limitar != null) limitar.validarSemantica();

        // 6. Guardar
        if (guardar != null) guardar.validarSemantica();

        return TipoDato.VOID;
    }

    // ── Generación del script Python ──────────────────────────────────────────

    @Override
    public String generarPython() {
        StringBuilder script = new StringBuilder();

        // Cabecera
        script.append("import pandas as pd\n");
        script.append("import sys\n\n");

        // Leer CSV
        script.append(desde.generarPython());

        // Filtrar filas
        if (donde != null)  script.append(donde.generarPython());

        // Ordenar
        if (ordenar != null) script.append(ordenar.generarPython());

        // Limitar filas
        if (limitar != null) script.append(limitar.generarPython());

        // Proyección (TRAER columnas / DISTINTO / agregación)
        // Si hay agregación, la selección ya imprime el escalar y terminamos
        if (seleccion.tieneAgregacion()) {
            script.append(seleccion.generarPython());
            return script.toString();
        }

        // Proyección de columnas y/o DISTINTO
        script.append(seleccion.generarPython());

        // Salida: GUARDAR EN o imprimir resultado
        if (guardar != null) {
            script.append(guardar.generarPython());
            // También mostramos en pantalla para la tabla de la UI
            script.append("print(df.to_csv(index=False))\n");
        } else {
            script.append("print(df.to_csv(index=False))\n");
        }

        return script.toString();
    }
}
