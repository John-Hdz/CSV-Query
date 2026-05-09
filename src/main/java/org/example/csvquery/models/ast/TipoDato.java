package org.example.csvquery.models.ast;

public enum TipoDato
{
    ENTERO,
    DECIMAL,
    CADENA,
    BOOLEANO, // Resultado de comparaciones como > o <
    VOID,     // Para nodos que no devuelven valor
    ERROR     // Para marcar nodos con fallos semánticos
}
