package com.piano.xr;

import javafx.application.Application;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
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

    @Override
    public void start(Stage stage) throws Exception {
        String pdfPath = getParameters().getRaw().get(0);
        document = PDDocument.load(new File(pdfPath));
        renderer = new PDFRenderer(document);

        // 1. Setup ScrollPane for the PDF content
        scrollPane = new ScrollPane();
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setStyle("-fx-background-color:transparent; -fx-background: transparent;");

        // Load the initial page data
        loadPage(0);

        // 2. Main container that holds both music and the wooden frame
        rootLayout = new StackPane(scrollPane);
        
        // 3. Apply the wooden "Baroque" overlay
        applyBaroqueFrame(rootLayout);

        // Calculate dynamic height based on the first stave detected
        double initialStaveHeight = (systemTops.size() > 1) ? (systemTops.get(1) - systemTops.get(0)) : 450;
        
        // Window width = PDF width + side wood space
        Scene scene = new Scene(rootLayout, pdfImageView.getImage().getWidth() + 260, initialStaveHeight + 160);

        stage.initStyle(javafx.stage.StageStyle.UNDECORATED);
        stage.setAlwaysOnTop(true);

        // Listen for keyboard/pedal (Down Arrow/Space) to move to the next system or page
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DOWN || event.getCode() == KeyCode.SPACE) {
                jumpToNext();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                stage.close();
            }
        });

        stage.setScene(scene);
        stage.show();
    }

    private void loadPage(int pageIndex) {
        try {
            BufferedImage bImage = renderer.renderImageWithDPI(pageIndex, 150);
            systemTops = findSystems(bImage);
            pdfImageView = new ImageView(SwingFXUtils.toFXImage(bImage, null));
            pdfImageView.setPreserveRatio(true);
            scrollPane.setContent(pdfImageView);

            currentPage = pageIndex;
            currentSystemIndex = 0;
            scrollToSystem(0);
        } catch (Exception e) {
            e.printStackTrace();
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

    private void scrollToSystem(int index) {
        double imageHeight = pdfImageView.getImage().getHeight();
        double viewportHeight = scrollPane.getViewportBounds().getHeight();
        if (imageHeight > viewportHeight) {
            scrollPane.setVvalue((double) systemTops.get(index) / (imageHeight - viewportHeight));
        }
    }

    private void applyBaroqueFrame(StackPane root) {
        try {
            // Load custom wooden assets from src/main/resources
            ImageView leftS = new ImageView(new Image(getClass().getResourceAsStream("/left_scroll.png")));
            ImageView rightS = new ImageView(new Image(getClass().getResourceAsStream("/right_scroll.png")));
            ImageView topWood = new ImageView(new Image(getClass().getResourceAsStream("/top_bar.jpg")));
            ImageView bottomWood = new ImageView(new Image(getClass().getResourceAsStream("/bottom_ledge.jpg")));

            // Scale side "S" scrolls to frame the music
            leftS.setPreserveRatio(true); leftS.setFitHeight(500);
            rightS.setPreserveRatio(true); rightS.setFitHeight(500);

            // Set horizontal bars to span the window width
            topWood.setFitWidth(2000); topWood.setPreserveRatio(false); topWood.setFitHeight(40);
            bottomWood.setFitWidth(2000); bottomWood.setPreserveRatio(false); bottomWood.setFitHeight(80);

            // Overlay layout using BorderPane
            BorderPane frameOverlay = new BorderPane();
            frameOverlay.setLeft(leftS);
            frameOverlay.setRight(rightS);
            frameOverlay.setTop(topWood);
            frameOverlay.setBottom(bottomWood);
            
            // Critical for usability: ignore mouse clicks on wood so music can still be scrolled/interacted with
            frameOverlay.setPickOnBounds(false); 
            
            root.getChildren().add(frameOverlay);
            root.setStyle("-fx-background-color: #2b1d12;"); // Fills gaps with dark mahogany color
        } catch (Exception e) {
            System.err.println("Error: Images must be in src/main/resources. " + e.getMessage());
        }
    }

    private List<Integer> findSystems(BufferedImage image) {
        List<Integer> tops = new ArrayList<>();
        int width = image.getWidth();
        int height = image.getHeight();
        int minGapHeight = 60; 
        int whiteSpaceCounter = 0;
        boolean inSystem = false;

        // Scan Y-axis for horizontal black pixel density (staves)
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