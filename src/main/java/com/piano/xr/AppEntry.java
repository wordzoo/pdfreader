package com.piano.xr;

import javafx.application.Application;

public class AppEntry {
    public static void main(String[] args) {
        // We call the JavaFX launch from here. 
        // This hides the Application class from the initial JVM boot.
        Application.launch(MainApp.class, args);
    }
}