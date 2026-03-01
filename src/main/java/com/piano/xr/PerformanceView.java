package com.piano.xr;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

// Helper to track page/y pairing
class SnapPoint {
    int page;
    int y;
    SnapPoint(int p, int y) { this.page = p; this.y = y; }
}

public class PerformanceView extends Application {
    private static final int FLUSH_TOP_OFFSET = 30;
    
    private final GeminiVisionService visionService = new GeminiVisionService();
    private final ImageView bgImageView = new ImageView();
    private final ImageView pdfImageView = new ImageView();
    private final Pane musicClipPane = new Pane();
    
    private PDDocument document; // Keep alive as class field
    private PDFRenderer renderer;
    private List<SnapPoint> yPositions = new ArrayList<>();
    private int currentIndex = 0;
    private int currentLoadedPage = -1;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        FileChooser fileChooser = new FileChooser();
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        File pdfFile = fileChooser.showOpenDialog(stage);

        if (pdfFile == null) { stage.close(); return; }

        this.document = PDDocument.load(pdfFile); // Store in class field
        this.renderer = new PDFRenderer(document);

        StackPane root = new StackPane();
        // Setup UI (BG, musicClipPane, etc.)
        try {
            Image bgImg = new Image(getClass().getResourceAsStream("/music_stand_bg.jpg"));
            bgImageView.setImage(bgImg);
            bgImageView.setFitWidth(Screen.getPrimary().getBounds().getWidth());
            bgImageView.setPreserveRatio(true);
        } catch (Exception e) { root.setStyle("-fx-background-color: #2c1b0e;"); }

        musicClipPane.getChildren().add(pdfImageView);
        root.getChildren().addAll(bgImageView, musicClipPane);
        
        stage.setScene(new Scene(root));
        stage.setFullScreen(true);
        stage.show();
        
        analyzePdf(document);
    }

    private void analyzePdf(PDDocument document) {
        Task<List<SnapPoint>> analysisTask = new Task<List<SnapPoint>>() {
            @Override
            protected List<SnapPoint> call() throws Exception {
                List<SnapPoint> combined = new ArrayList<>();
                ObjectMapper mapper = new ObjectMapper();
                for (int i = 0; i < document.getNumberOfPages(); i++) {
                    BufferedImage bImage = renderer.renderImageWithDPI(i, 150);
                    String json = visionService.analyzePage(bImage);
                    String clean = json.replaceAll("```json", "").replaceAll("```", "").trim();
                    
                    JsonNode systems = mapper.readTree(clean).at("/candidates/0/content/parts/0/text");
                    List<Integer> pageY = mapper.readValue(mapper.readTree(systems.asText()).get("systems").toString(), new TypeReference<List<Integer>>(){});
                    for (Integer y : pageY) combined.add(new SnapPoint(i, y));
                }
                return combined;
            }
        };

        analysisTask.setOnSucceeded(e -> {
            this.yPositions = analysisTask.getValue();
            System.out.println("Total systems found: " + yPositions.size());
            
            // Only load the first snap if we actually found something
            if (yPositions != null && !yPositions.isEmpty()) {
                loadSnap(0);
            }
        });
        new Thread(analysisTask).start();
    }

    private void loadSnap(int index) {
        SnapPoint point = yPositions.get(index);
        System.out.println("Loading page: " + point.page); // 1. Is this being reached?

        try {
            BufferedImage bImage = renderer.renderImageWithDPI(point.page, 150);
            System.out.println("BufferedImage created: " + (bImage != null)); // 2. Did rendering work?
            
            Platform.runLater(() -> {
                pdfImageView.setImage(SwingFXUtils.toFXImage(bImage, null));
                pdfImageView.setVisible(true); // Ensure it's visible
                System.out.println("ImageView updated."); // 3. Did UI update?
            });
        } catch (Exception e) { 
            e.printStackTrace(); // Catch rendering errors
        }
    }
}