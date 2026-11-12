module com.relian.launcher {
    requires javafx.controls;
    requires java.desktop;
    requires java.net.http;
    requires jdk.httpserver;

    exports com.relian.launcher;
    opens com.relian.launcher to javafx.graphics;
}
