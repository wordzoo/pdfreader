package com.piano.xr;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

public class PerformanceView extends Application {
    private File pdfFile;
    private File txtFile;
    private PDDocument document;
    private List<Integer> systemSnaps;
    private ScrollPane scrollPane;
    private int currentIndex = 0;

    public PerformanceView(File pdf, File txt) {
        this.pdfFile = pdf;
        this.txtFile = txt;
    }

    @Override
    public void start(Stage stage) throws Exception {
        document = PDDocument.load(pdfFile);
        systemSnaps = Files.readAllLines(txtFile.toPath()).stream()
                           .map(Integer::parseInt).collect(Collectors.toList());

        scrollPane = new ScrollPane();
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        
        // VIEWPORT HEIGHT STATE 5 (1/3 Screen)
        double screenHeight = Screen.getPrimary().getBounds().getHeight();
        scrollPane.setMaxHeight(screenHeight / 3.0);
        
        PDFRenderer renderer = new PDFRenderer(document);
        ImageView view = new ImageView(SwingFXUtils.toFXImage(renderer.renderImageWithDPI(0, 150), null));
        scrollPane.setContent(view);

        Scene scene = new Scene(new StackPane(scrollPane));
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN || e.getCode() == KeyCode.SPACE) jump(1);
            if (e.getCode() == KeyCode.UP) jump(-1);
        });

        stage.setScene(scene);
        stage.setFullScreen(true);
        stage.setAlwaysOnTop(true);
        stage.show();
        
        Platform.runLater(() -> scrollToSystem(0));
    }

    private void jump(int dir) {
        currentIndex = Math.max(0, Math.min(systemSnaps.size() - 1, currentIndex + dir));
        scrollToSystem(currentIndex);
    }

    private void scrollToSystem(int index) {
        double vH = scrollPane.getHeight();
        double cH = scrollPane.getContent().getBoundsInLocal().getHeight();
        double targetY = systemSnaps.get(index);
        
        // STATE 5: 33% Offset (Landing the Treble Clef in the sweet spot)
        double scrollPos = (targetY - (vH * 0.33)) / (cH - vH);
        scrollPane.setVvalue(Math.max(0, Math.min(1.0, scrollPos)));
    }
}