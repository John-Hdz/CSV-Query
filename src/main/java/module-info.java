module org.example.csvquery {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires com.opencsv;

    opens org.example.csvquery to javafx.fxml;
    exports org.example.csvquery;
}