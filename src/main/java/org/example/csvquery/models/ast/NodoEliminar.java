package org.example.csvquery.models.ast;

import java.io.BufferedReader;
import java.io.FileReader;

public class NodoEliminar implements NodoAST {

    private String archivo;
    private NodoAST condicion;

    public NodoEliminar(String archivo,
                        NodoAST condicion) {

        this.archivo = archivo;
        this.condicion = condicion;
    }

    @Override
    public TipoDato validarSemantica()
            throws Exception {

        String ruta =
                archivo.replace("\"", "");

        BufferedReader br =
                new BufferedReader(
                        new FileReader(ruta)
                );

        String encabezado =
                br.readLine();

        br.close();

        if (encabezado == null) {

            throw new Exception(
                    "El archivo CSV está vacío."
            );
        }

        condicion.validarSemantica();

        return TipoDato.CADENA;
    }

    @Override
    public String generarPython() {

        StringBuilder py =
                new StringBuilder();

        py.append(
                "import pandas as pd\n\n"
        );

        py.append("df = pd.read_csv(")
                .append(archivo)
                .append(")\n\n");

        String condicionPython =
                condicion.generarPython();

        py.append("condicion = ")
                .append(condicionPython)
                .append("\n\n");

        py.append(
                "df = df.loc[~condicion]\n\n"
        );

        py.append("df.to_csv(")
                .append(archivo)
                .append(", index=False)\n\n");

        py.append(
                "print(df.to_csv(index=False))"
        );

        return py.toString();
    }

    @Override
    public NodoInfo toInfo() {

        NodoInfo raiz =
                new NodoInfo(
                        "ELIMINAR",
                        archivo,
                        NodoInfo.Categoria.CONSULTA,
                        true
                );

        raiz.hijos.add(
                condicion.toInfo()
        );

        return raiz;
    }
}