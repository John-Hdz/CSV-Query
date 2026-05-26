package org.example.csvquery.models.ast;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NodoActualizar implements NodoAST {

    private String archivo;
    private List<String> columnas;
    private List<String> valores;
    private NodoAST condicion;

    public NodoActualizar(String archivo,
                          List<String> columnas,
                          List<String> valores,
                          NodoAST condicion) {

        this.archivo = archivo;
        this.columnas = columnas;
        this.valores = valores;
        this.condicion = condicion;
    }

    @Override
    public TipoDato validarSemantica() throws Exception {

        // ─────────────────────────────────────────────
        // VALIDAR CANTIDAD DE COLUMNAS Y VALORES
        // ─────────────────────────────────────────────

        if (columnas.size() != valores.size()) {

            throw new Exception(
                    "La cantidad de columnas y valores no coincide."
            );
        }

        // ─────────────────────────────────────────────
        // VALIDAR EXISTENCIA DEL CSV
        // ─────────────────────────────────────────────

        String ruta = archivo.replace("\"", "");

        BufferedReader br =
                new BufferedReader(
                        new FileReader(ruta)
                );

        String encabezado = br.readLine();

        br.close();

        if (encabezado == null) {

            throw new Exception(
                    "El archivo CSV está vacío."
            );
        }

        // ─────────────────────────────────────────────
        // VALIDAR COLUMNAS
        // ─────────────────────────────────────────────

        String[] columnasCSV = encabezado.split(",");

        Set<String> columnasExistentes =
                new HashSet<>();

        for (String col : columnasCSV) {

            columnasExistentes.add(
                    col.trim()
            );
        }

        for (String colUpdate : columnas) {

            if (!columnasExistentes.contains(
                    colUpdate.trim()
            )) {

                throw new Exception(
                        "La columna '" + colUpdate +
                                "' no existe en el CSV."
                );
            }
        }

        // ─────────────────────────────────────────────
        // VALIDAR CONDICIÓN
        // ─────────────────────────────────────────────

        condicion.validarSemantica();

        return TipoDato.CADENA;
    }

    @Override
    public String generarPython() {

        StringBuilder py = new StringBuilder();

        py.append("import pandas as pd\n\n");

        py.append("df = pd.read_csv(")
                .append(archivo)
                .append(")\n\n");

        // ─────────────────────────────────────────────
        // GUARDAR CONDICIÓN UNA SOLA VEZ
        // ─────────────────────────────────────────────

        String condicionPython =
                condicion.generarPython();

        py.append("condicion = ")
                .append(condicionPython)
                .append("\n\n");

        // ─────────────────────────────────────────────
        // ACTUALIZAR COLUMNAS
        // ─────────────────────────────────────────────

        for (int i = 0; i < columnas.size(); i++) {

            String columna = columnas.get(i);
            String valor = valores.get(i);

            py.append("df.loc[");

            py.append("condicion");

            py.append(", ");

            py.append("\"")
                    .append(columna)
                    .append("\"");

            py.append("] = ");

            // NÚMERO
            if (valor.matches("-?\\d+(\\.\\d+)?")) {

                py.append(valor);
            }

            // STRING
            else {

                py.append("\"")
                        .append(valor.replace("\"", ""))
                        .append("\"");
            }

            py.append("\n");
        }

        py.append("\n");

        // ─────────────────────────────────────────────
        // GUARDAR CSV
        // ─────────────────────────────────────────────

        py.append("df.to_csv(")
                .append(archivo)
                .append(", index=False)\n\n");

        py.append("print(df.to_csv(index=False))");

        return py.toString();
    }

    @Override
    public NodoInfo toInfo() {

        NodoInfo raiz = new NodoInfo(
                "ACTUALIZAR",
                archivo,
                NodoInfo.Categoria.CONSULTA,
                true
        );

        for (String col : columnas) {

            raiz.hijos.add(
                    new NodoInfo(
                            "SET",
                            col,
                            NodoInfo.Categoria.COLUMNA,
                            true
                    )
            );
        }

        raiz.hijos.add(condicion.toInfo());

        return raiz;
    }
}