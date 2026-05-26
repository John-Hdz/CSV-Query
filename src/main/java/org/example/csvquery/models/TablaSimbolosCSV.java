package org.example.csvquery.models;

import com.opencsv.CSVReader;
import org.example.csvquery.models.ast.TipoDato;

import java.io.FileReader;
import java.util.HashMap;
import java.util.Map;

public class TablaSimbolosCSV
{

    // Diccionario que guarda "nombre_columna" -> TipoDato
    private static Map<String, TipoDato> columnas = new HashMap<>();

    public static void cargarArchivo(String rutaArchivo) throws Exception {
        columnas.clear();

        try (CSVReader reader = new CSVReader(new FileReader(rutaArchivo))) {
            String[] encabezados = reader.readNext();
            String[] primeraFilaDatos = reader.readNext(); // Leemos una fila para adivinar el tipo

            if (encabezados == null) {
                throw new Exception("El archivo CSV está vacío.");
            }

            for (int i = 0; i < encabezados.length; i++) {
                String nombreCol = encabezados[i].trim();
                TipoDato tipo = TipoDato.CADENA; // Por defecto es cadena

                // Si hay datos, intentamos adivinar si es un número
                if (primeraFilaDatos != null && i < primeraFilaDatos.length) {
                    tipo = inferirTipo(primeraFilaDatos[i].trim());
                }

                // Guardamos la columna y su tipo en la Tabla de Símbolos
                columnas.put(nombreCol, tipo);
            }

            System.out.println("Tabla de símbolos cargada: " + columnas.keySet());

        } catch (Exception e) {
            throw new Exception("Error al leer el archivo CSV para validación semántica: " + e.getMessage());
        }
    }

    public static boolean existeColumna(String nombre) {
        return columnas.containsKey(nombre);
    }

    public static TipoDato obtenerTipoColumna(String nombre) {
        return columnas.getOrDefault(nombre, TipoDato.ERROR);
    }

    private static TipoDato inferirTipo(String valor) {
        if (valor.isEmpty()) return TipoDato.CADENA;

        //(Solo dígitos)
        if (valor.matches("-?\\d+")) {
            return TipoDato.ENTERO;
        }
        // (Dígitos con un punto)
        if (valor.matches("-?\\d+\\.\\d+")) {
            return TipoDato.DECIMAL;
        }
        // boolean
        if (valor.equalsIgnoreCase("true") || valor.equalsIgnoreCase("false")) {
            return TipoDato.BOOLEANO;
        }

        // texto plano
        return TipoDato.CADENA;
    }
}
