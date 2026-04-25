package org.example.csvquery.models;

public class Token {
    private String lexema;
    private String nombre;
    private int id;

    public Token(String lexema, String nombre, int id) {
        this.lexema = lexema;
        this.nombre = nombre;
        this.id = id;
    }

    @Override
    public String toString() {
        return String.format("ID: %-4d | Token: %-20s | Lexema: '%s'", id, nombre, lexema);
    }

    public String getLexema() { return lexema; }
    public String getNombre() { return nombre; }
    public int getId() { return id; }
}