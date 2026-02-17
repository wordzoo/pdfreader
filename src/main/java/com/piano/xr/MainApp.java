package com.piano.xr;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainApp extends Application {

    private PDDocument document;
    private PDFRenderer renderer;
    private int currentPage = 0;
    private int currentSystemIndex = 0;
    private List<Integer> systemTops;

    private ScrollPane scrollPane;
    private ImageView pdfImageView;
    private StackPane rootLayout;
    private ImageView standView;
    private Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        this.primaryStage = stage;
        String pdfPath = getParameters().getRaw().isEmpty() ? null : getParameters().getRaw().get(0);
        
        rootLayout = new StackPane();
        
        // 1. Initialize ScrollPane
        scrollPane = new ScrollPane();
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        // This ensures the ScrollPane content is centered if it's smaller than the viewport
        scrollPane.setFitToWidth(true); 
        scrollPane.setStyle("-fx-background-color:transparent; -fx-background: transparent; -fx-border-color: transparent;");

        double screenHeight = Screen.getPrimary().getBounds().getHeight();
        scrollPane.setMaxHeight(screenHeight / 3.0);
        scrollPane.setPrefHeight(screenHeight / 3.0);

        if (pdfPath != null) {
            loadNewPDF(new File(pdfPath));
        }

        applyBaroqueBackground(rootLayout);
        
        // 2. Add the ScrollPane to the center of the StackPane
        rootLayout.getChildren().add(scrollPane);
        StackPane.setAlignment(scrollPane, Pos.CENTER);

        Scene scene = new Scene(rootLayout);
        
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN || event.getCode() == KeyCode.SPACE) jumpToNext();
            else if (event.getCode() == KeyCode.UP) jumpToPrevious();
            else if (event.getCode() == KeyCode.ESCAPE) stage.close();
        });

        stage.setScene(scene);
        stage.setFullScreen(true);
        stage.setFullScreenExitHint(""); 
        stage.setAlwaysOnTop(true);
        stage.show();
    }

    private void loadNewPDF(File file) {
        try {
            if (document != null) document.close();
            document = PDDocument.load(file);
            renderer = new PDFRenderer(document);
            loadPage(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadPage(int pageIndex) {
        try {
            BufferedImage bImage = renderer.renderImageWithDPI(pageIndex, 150);
            systemTops = findSystems(bImage);
            pdfImageView = new ImageView(SwingFXUtils.toFXImage(bImage, null));
            pdfImageView.setPreserveRatio(true);
            
            double screenWidth = Screen.getPrimary().getBounds().getWidth();
            pdfImageView.setFitWidth(screenWidth - 450); 

            pdfImageView.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    openFilePicker();
                }
            });

            // 3. FIX: Wrap ImageView in a VBox to force centering within the ScrollPane
            VBox centeringWrapper = new VBox(pdfImageView);
            centeringWrapper.setAlignment(Pos.CENTER);
            centeringWrapper.setStyle("-fx-background-color: transparent;");
            
            scrollPane.setContent(centeringWrapper);
            
            currentPage = pageIndex;
            currentSystemIndex = 0;
            scrollToSystem(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void openFilePicker() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Sheet Music PDF");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        File selectedFile = fileChooser.showOpenDialog(primaryStage);
        if (selectedFile != null) {
            loadNewPDF(selectedFile);
        }
    }

    private void jumpToNext() {
        if (currentSystemIndex < systemTops.size() - 1) {
            currentSystemIndex++;
            scrollToSystem(currentSystemIndex);
        } else if (currentPage < document.getNumberOfPages() - 1) {
            loadPage(currentPage + 1);
        }
    }

    private void jumpToPrevious() {
        if (currentSystemIndex > 0) {
            currentSystemIndex--;
            scrollToSystem(currentSystemIndex);
        } else if (currentPage > 0) {
            loadPage(currentPage - 1);
            currentSystemIndex = systemTops.size() - 1; 
            scrollToSystem(currentSystemIndex);
        }
    }

    private void scrollToSystem(int index) {
        double imageHeight = pdfImageView.getImage().getHeight();
        double viewportHeight = scrollPane.getHeight();
        if (imageHeight > viewportHeight) {
            // Calculate scroll based on the image's height within the wrapper
            double scrollPos = (double) systemTops.get(index) / (imageHeight - viewportHeight);
            scrollPane.setVvalue(scrollPos);
        }
    }

    private void applyBaroqueBackground(StackPane root) {
        try {
            Image standImg = new Image(getClass().getResourceAsStream("/music_stand_bg.jpg"));
            standView = new ImageView(standImg);
            standView.setPreserveRatio(true); 
            standView.fitWidthProperty().bind(root.widthProperty());
            
            if (root.getChildren().isEmpty()) root.getChildren().add(standView);
            else root.getChildren().add(0, standView);
            
            root.setStyle("-fx-background-color: black;");
        } catch (Exception e) {
            System.err.println("Background error: " + e.getMessage());
        }
    }

    private List<Integer> findSystems(BufferedImage image) {
        List<Integer> tops = new ArrayList<>();
        int width = image.getWidth();
        int height = image.getHeight();
        int minGapHeight = 60; 
        int whiteSpaceCounter = 0;
        boolean inSystem = false;

        for (int y = 0; y < height; y++) {
            int blackPixels = 0;
            for (int x = 0; x < width; x++) {
                if ((image.getRGB(x, y) & 0xFF) < 150) blackPixels++;
            }
            if ((double) blackPixels / width > 0.005) {
                if (!inSystem) { tops.add(y); inSystem = true; }
                whiteSpaceCounter = 0;
            } else if (inSystem) {
                whiteSpaceCounter++;
                if (whiteSpaceCounter > minGapHeight) inSystem = false;
            }
        }
        return tops;
    }
}