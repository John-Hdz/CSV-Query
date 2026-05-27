package org.example.csvquery.models.ast;

import java.util.List;

public class NodoInsertar implements NodoAST {

    private String archivo;
    private List<String> columnas;
    private List<String> valores;

    public NodoInsertar(String archivo,
                        List<String> columnas,
                        List<String> valores) {

        this.archivo = archivo;
        this.columnas = columnas;
        this.valores = valores;
    }

    @Override
    public TipoDato validarSemantica() throws Exception {

        if (columnas.size() != valores.size()) {

            throw new Exception(
                    "La cantidad de columnas y valores no coincide."
            );
        }

        String ruta = archivo.replace("\"", "");

        java.io.BufferedReader br =
                new java.io.BufferedReader(
                        new java.io.FileReader(ruta)
                );

        String encabezado = br.readLine();

        br.close();

        if (encabezado == null) {

            throw new Exception(
                    "El archivo CSV está vacío."
            );
        }


        // VALIDAR COLUMNAS

        String[] columnasCSV = encabezado.split(",");

        java.util.Set<String> columnasExistentes =
                new java.util.HashSet<>();

        for (String col : columnasCSV) {

            columnasExistentes.add(
                    col.trim()
            );
        }

        for (String colInsert : columnas) {

            if (!columnasExistentes.contains(
                    colInsert.trim()
            )) {

                throw new Exception(
                        "La columna '" + colInsert +
                                "' no existe en el CSV."
                );
            }
        }

        return TipoDato.CADENA;
    }

    @Override
    public String generarPython() {

        StringBuilder py = new StringBuilder();

        py.append("import pandas as pd\n\n");

        py.append("df = pd.read_csv(")
                .append(archivo)
                .append(")\n\n");

        py.append("nueva_fila = {\n");

        for (int i = 0; i < columnas.size(); i++) {

            py.append("\"")
                    .append(columnas.get(i))
                    .append("\": ");

            String valor = valores.get(i);

            if (valor.matches("-?\\d+(\\.\\d+)?")) {

                py.append(valor);
            }
            else {

                py.append("\"")
                        .append(valor.replace("\"", ""))
                        .append("\"");
            }

            if (i < columnas.size() - 1) {
                py.append(",");
            }

            py.append("\n");
        }

        py.append("}\n\n");

        py.append("df.loc[len(df)] = nueva_fila\n\n");

        py.append("df.to_csv(")
                .append(archivo)
                .append(", index=False)\n\n");

        py.append("print(df.to_csv(index=False))");

        return py.toString();
    }

    @Override
    public NodoInfo toInfo() {

        NodoInfo raiz = new NodoInfo(
                "INSERTAR",
                archivo,
                NodoInfo.Categoria.CONSULTA,
                true
        );

        for (String col : columnas) {

            raiz.hijos.add(
                    new NodoInfo(
                            "COLUMNA",
                            col,
                            NodoInfo.Categoria.COLUMNA,
                            true
                    )
            );
        }

        return raiz;
    }
}