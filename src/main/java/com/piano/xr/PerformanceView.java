package com.piano.xr;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
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
import java.util.List;
import java.util.stream.Collectors;

public class PerformanceView extends Application {
    // 10 pixels lowers the scroll point; negative values raise it.
    private static final int FLUSH_TOP_OFFSET = 30; 

    private File pdfFile, txtFile;
    private PDDocument document;
    private List<String> rawSnaps; 
    private int currentIndex = 0;
    private int currentLoadedPage = -1;
    private PDFRenderer renderer;
    
    private ImageView bgImageView = new ImageView();
    private ImageView pdfImageView = new ImageView();
    private Pane musicClipPane = new Pane(); 

    public PerformanceView(File pdf, File txt) {
        this.pdfFile = pdf;
        this.txtFile = txt;
    }

    @Override
    public void start(Stage stage) throws Exception {
        document = PDDocument.load(pdfFile);
        renderer = new PDFRenderer(document);
        rawSnaps = Files.readAllLines(txtFile.toPath()).stream()
                        .filter(l -> l.contains(":"))
                        .collect(Collectors.toList());

        StackPane root = new StackPane();
        double screenWidth = Screen.getPrimary().getBounds().getWidth();
        double screenHeight = Screen.getPrimary().getBounds().getHeight();

        // Background: Your fixed logic for Fit Width and Preserved Ratio
        try {
            Image bgImg = new Image(getClass().getResourceAsStream("/music_stand_bg.jpg"));
            bgImageView.setImage(bgImg);
            bgImageView.setFitWidth(screenWidth);            
            bgImageView.setPreserveRatio(true); 
        } catch (Exception e) {
            root.setStyle("-fx-background-color: #2c1b0e;");
        }

        // Viewport: 1/3 monitor height
        double viewportHeight = screenHeight / 3.0;
        musicClipPane.setMaxSize(screenWidth, viewportHeight);
        musicClipPane.setPrefSize(screenWidth, viewportHeight);
        musicClipPane.setClip(new javafx.scene.shape.Rectangle(screenWidth, viewportHeight));

        // Absolute Positioning & Horizontal Centering
        musicClipPane.getChildren().add(pdfImageView);
        pdfImageView.layoutXProperty().bind(musicClipPane.widthProperty().subtract(pdfImageView.fitWidthProperty()).divide(2));

        root.getChildren().addAll(bgImageView, musicClipPane);

        Scene scene = new Scene(root);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DOWN || e.getCode() == KeyCode.SPACE || e.getCode() == KeyCode.RIGHT) jump(1);
            else if (e.getCode() == KeyCode.UP || e.getCode() == KeyCode.LEFT) jump(-1);
            else if (e.getCode() == KeyCode.ESCAPE) stage.close();
        });

        stage.setScene(scene);
        stage.setFullScreen(true);
        stage.show();

        if (!rawSnaps.isEmpty()) loadSnap(0);
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

        if (page != currentLoadedPage) {
            try {
                currentLoadedPage = page;
                BufferedImage bImage = renderer.renderImageWithDPI(page, 150);
                pdfImageView.setImage(SwingFXUtils.toFXImage(bImage, null));
                pdfImageView.setPreserveRatio(true);
                pdfImageView.setFitWidth(Screen.getPrimary().getBounds().getWidth() - 400);
            } catch (Exception e) { e.printStackTrace(); }
        }

        // Apply Flush Top with Offset Constant
        Platform.runLater(() -> {
            // Negative 'y' moves content up; adding the offset pushes it back down.
            pdfImageView.setLayoutY(-y + FLUSH_TOP_OFFSET); 
        });
    }
}