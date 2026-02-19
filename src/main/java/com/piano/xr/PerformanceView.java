package com.piano.xr;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class PerformanceView extends Application {
    private File pdfFile, txtFile;
    private PDDocument document;
    private List<String> rawSnaps; // Stores "page:y" strings
    private ScrollPane scrollPane;
    private int currentIndex = 0;
    private int currentLoadedPage = -1;
    private PDFRenderer renderer;
    private ImageView pdfImageView = new ImageView();

    public PerformanceView(File pdf, File txt) {
        this.pdfFile = pdf;
        this.txtFile = txt;
    }

    @Override
    public void start(Stage stage) throws Exception {
        document = PDDocument.load(pdfFile);
        renderer = new PDFRenderer(document);
        rawSnaps = Files.readAllLines(txtFile.toPath());

        StackPane root = new StackPane();
        
        // FIX: Fit Width Background
        try {
            Image bgImg = new Image(getClass().getResourceAsStream("/music_stand_bg.jpg"));
            BackgroundSize bgSize = new BackgroundSize(100, 100, true, true, false, false);
            root.setBackground(new Background(new BackgroundImage(bgImg, 
                BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT, 
                BackgroundPosition.CENTER, bgSize)));
        } catch (Exception e) { root.setStyle("-fx-background-color: #2c1b0e;"); }

        scrollPane = new ScrollPane();
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

        double screenHeight = Screen.getPrimary().getBounds().getHeight();
        scrollPane.setMaxHeight(screenHeight / 3.0);
        scrollPane.setPrefHeight(screenHeight / 3.0);

        VBox container = new VBox(pdfImageView);
        container.setAlignment(Pos.CENTER);
        scrollPane.setContent(container);
        root.getChildren().add(scrollPane);

        Scene scene = new Scene(root);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN || e.getCode() == KeyCode.SPACE) jump(1);
            if (e.getCode() == KeyCode.UP) jump(-1);
            if (e.getCode() == KeyCode.ESCAPE) stage.close();
        });

        stage.setScene(scene);
        stage.setFullScreen(true);
        stage.show();

        loadSnap(0);
    }

    private void jump(int dir) {
        int next = currentIndex + dir;
        if (next >= 0 && next < rawSnaps.size()) {
            currentIndex = next;
            loadSnap(currentIndex);
        }
    }

    private void loadSnap(int index) {
        String[] parts = rawSnaps.get(index).split(":");
        int page = Integer.parseInt(parts[0]);
        int y = Integer.parseInt(parts[1]);

        // Only re-render if we actually changed pages
        if (page != currentLoadedPage) {
            try {
                currentLoadedPage = page;
                BufferedImage bImage = renderer.renderImageWithDPI(page, 150);
                pdfImageView.setImage(SwingFXUtils.toFXImage(bImage, null));
                pdfImageView.setPreserveRatio(true);
                pdfImageView.setFitWidth(Screen.getPrimary().getBounds().getWidth() - 300);
            } catch (Exception e) { e.printStackTrace(); }
        }

        // Apply Flush-Top Scroll Math
        Platform.runLater(() -> {
            double cH = scrollPane.getContent().getBoundsInLocal().getHeight();
            double vH = scrollPane.getViewportBounds().getHeight();
            double scrollRange = cH - vH;
            double scrollPos = (scrollRange > 0) ? (double)y / scrollRange : 0;
            scrollPane.setVvalue(Math.max(0, Math.min(1.0, scrollPos)));
        });
    }
}