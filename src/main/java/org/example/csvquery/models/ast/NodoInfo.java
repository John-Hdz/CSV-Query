package org.example.csvquery.models.ast;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO de solo lectura que describe un nodo del AST para la UI.
 * Cada NodoXxx construye uno en su método toInfo().
 *
 * El MainController recorre el árbol de NodoInfo para dibujar
 * el panel sintáctico sin acoplar la UI a los nodos del AST.
 */
public class NodoInfo {

    public enum Categoria {
        CONSULTA,    // NodoConsulta (raíz)
        CLAUSULA,    // TRAER, DESDE, DONDE, ORDENAR, LIMITAR, GUARDAR
        COLUMNA,     // nombre de columna
        LITERAL,     // valor constante (número, cadena)
        OPERADOR,    // operador de comparación o lógico (AND/OR)
        AGREGACION,  // CONTAR, SUMA, PROMEDIO…
        OPCIONAL     // nodo presente pero no obligatorio (indica ausencia con gris)
    }

    // ── Datos del nodo ────────────────────────────────────────────────────────
    public final String    etiqueta;    // texto principal, ej: "NodoConsulta"
    public final String    detalle;     // texto secundario, ej: "datos.csv"
    public final Categoria categoria;
    public final boolean   presente;    // false = nodo ausente (se muestra en gris)

    // ── Hijos en el árbol ─────────────────────────────────────────────────────
    public final List<NodoInfo> hijos = new ArrayList<>();

    public NodoInfo(String etiqueta, String detalle, Categoria categoria, boolean presente) {
        this.etiqueta  = etiqueta;
        this.detalle   = detalle;
        this.categoria = categoria;
        this.presente  = presente;
    }

    /** Constructor para nodos presentes */
    public NodoInfo(String etiqueta, String detalle, Categoria categoria) {
        this(etiqueta, detalle, categoria, true);
    }

    /** Agrega un hijo y devuelve this para encadenar */
    public NodoInfo hijo(NodoInfo h) {
        hijos.add(h);
        return this;
    }
}
