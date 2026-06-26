module com.br.minehub.main {

    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.base;

    requires com.hierynomus.sshj;

    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.fontawesome5;

    requires java.net.http;

    requires org.java_websocket;

    requires com.fasterxml.jackson.databind;

    exports com.br.minehub.main;
    exports com.br.minehub.main.model;

    opens com.br.minehub.main to javafx.fxml;
    opens com.br.minehub.main.model to javafx.base;
    exports com.br.minehub.main.service.pterodactyl.model;
    opens com.br.minehub.main.service.pterodactyl.model to javafx.base;
}