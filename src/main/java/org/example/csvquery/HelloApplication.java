package org.example.csvquery;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.csvquery.models.Lexer;

import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("csv_query.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1080, 720);
        stage.setTitle("Hello!");
        stage.setScene(scene);
        stage.show();

        // 1. Instanciar el lexer cargando el CSV descargado de Google Sheets
        Lexer lexer = new Lexer("src/main/resources/CSV/Matriz de transicion.csv");

        // 2. Analizar el archivo txt con la consulta SQL / CSV-Query
        // Ejemplo de txt: TRAER id, nombre DESDE "archivo.csv" DONDE edad >= 18;
        lexer.analizarArchivo("src/main/resources/CodeExample/consulta.txt");

        // 3. Imprimir las estructuras
        lexer.imprimirTablaSimbolos();
        lexer.imprimirPilaErrores();
    }
}
