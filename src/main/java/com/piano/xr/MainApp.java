package com.piano.xr;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import nu.pattern.OpenCV;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainApp extends Application {
    
    static { 
        try { OpenCV.loadLocally(); } catch (Throwable e) { OpenCV.loadShared(); }
    }

    private PDDocument document;
    private PDFRenderer renderer;
    private int currentPage = -1;
    private int currentSystemIndex = 0;
    private List<Integer> systemTops = new ArrayList<>();
    private Mat clefTemplate;

    private ScrollPane scrollPane;
    private ImageView pdfImageView;
    private Stage primaryStage;

    @Override
    public void start(Stage stage) throws Exception {
        this.primaryStage = stage;
        
        // Load the anchor image from resources
        try (InputStream is = getClass().getResourceAsStream("/treble_clef_anchor.png")) {
            if (is != null) {
                BufferedImage bi = ImageIO.read(is);
                clefTemplate = bufferedImageToMat(bi);
                Imgproc.cvtColor(clefTemplate, clefTemplate, Imgproc.COLOR_BGR2GRAY);
            }
        }

        StackPane rootLayout = new StackPane();
        scrollPane = new ScrollPane();
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setFitToWidth(true);
        // Make the scrollpane transparent to see the baroque background
        scrollPane.setStyle("-fx-background-color:transparent; -fx-background: transparent; -fx-border-color: transparent;");

        double screenHeight = Screen.getPrimary().getBounds().getHeight();
        scrollPane.setMaxHeight(screenHeight / 3.0);
        scrollPane.setPrefHeight(screenHeight / 3.0);

        // Apply the background image
        applyBaroqueBackground(rootLayout);
        
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

        String pdfPath = getParameters().getRaw().isEmpty() ? null : getParameters().getRaw().get(0);
        if (pdfPath != null) loadNewPDF(new File(pdfPath));
    }

    private void loadNewPDF(File file) {
        try {
            if (document != null) document.close();
            document = PDDocument.load(file);
            renderer = new PDFRenderer(document);
            loadPage(0, true);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void loadPage(int pageIndex, boolean jumpToTop) {
        try {
            currentPage = pageIndex;
            BufferedImage bImage = renderer.renderImageWithDPI(pageIndex, 150);
            
            Mat source = bufferedImageToMat(bImage);
            // Search across multiple scales to handle varying PDF sizes
            systemTops = findClefsRobust(source);
            
            // Marker for Page 1 Title
            if (pageIndex == 0 && (systemTops.isEmpty() || systemTops.get(0) > 200)) {
                systemTops.add(0, 100); 
            }
            Collections.sort(systemTops);
            
            System.out.println("Page " + pageIndex + ": Found " + systemTops.size() + " systems.");

            pdfImageView = new ImageView(SwingFXUtils.toFXImage(matToBufferedImage(source), null));
            pdfImageView.setPreserveRatio(true);
            pdfImageView.setFitWidth(Screen.getPrimary().getBounds().getWidth() - 450);

            VBox wrapper = new VBox(pdfImageView);
            wrapper.setAlignment(Pos.CENTER);
            // Pad bottom so the final stave of the page can be centered
            wrapper.setPadding(new Insets(0, 0, Screen.getPrimary().getBounds().getHeight() / 2.0, 0));
            
            scrollPane.setContent(wrapper);
            currentSystemIndex = jumpToTop ? 0 : systemTops.size() - 1;
            
            Platform.runLater(() -> scrollToSystem(currentSystemIndex));
            source.release();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private List<Integer> findClefsRobust(Mat source) {
        List<Integer> yCoords = new ArrayList<>();
        if (clefTemplate == null) return yCoords;

        Mat gray = new Mat();
        Imgproc.cvtColor(source, gray, Imgproc.COLOR_BGR2GRAY);

        // Scan 5 different scales to find the best fit
        double[] scales = {0.6, 0.8, 1.0, 1.2, 1.4};
        
        for (double scale : scales) {
            Mat resT = new Mat();
            Imgproc.resize(clefTemplate, resT, new Size(0, 0), scale, scale, Imgproc.INTER_LINEAR);
            
            Mat result = new Mat();
            Imgproc.matchTemplate(gray, resT, result, Imgproc.TM_CCOEFF_NORMED);

            // Use 0.50 threshold to catch varied ink weights
            double threshold = 0.50; 
            for (int y = 0; y < result.rows(); y++) {
                for (int x = 0; x < result.cols(); x++) {
                    if (x < gray.cols() * 0.12) {
                        if (result.get(y, x)[0] > threshold) {
                            int centerY = y + (resT.rows() / 2);
                            boolean duplicate = false;
                            for (int existingY : yCoords) {
                                if (Math.abs(existingY - centerY) < 200) duplicate = true;
                            }
                            if (!duplicate) {
                                yCoords.add(centerY);
                                // Draw red box on the music to see what the AI found
                                Imgproc.rectangle(source, new Point(x, y), 
                                    new Point(x + resT.cols(), y + resT.rows()), new Scalar(0,0,255), 3);
                            }
                        }
                    }
                }
            }
            resT.release();
            result.release();
        }
        gray.release();
        return yCoords;
    }

    private void scrollToSystem(int index) {
        if (scrollPane.getContent() == null || systemTops.isEmpty()) return;
        double vH = scrollPane.getHeight();
        double cH = scrollPane.getContent().getBoundsInLocal().getHeight();
        double targetY = systemTops.get(index);
        double lift = vH * 0.10; // Apply the 10% lift for Xreal comfort
        double scrollPos = (targetY - (vH / 2.0) - lift) / (cH - vH);
        scrollPane.setVvalue(Math.max(0, Math.min(1.0, scrollPos)));
    }

    private void jumpToNext() {
        if (currentSystemIndex < systemTops.size() - 1) {
            currentSystemIndex++;
            scrollToSystem(currentSystemIndex);
        } else if (currentPage < document.getNumberOfPages() - 1) {
            loadPage(currentPage + 1, true);
        }
    }

    private void jumpToPrevious() {
        if (currentSystemIndex > 0) {
            currentSystemIndex--;
            scrollToSystem(currentSystemIndex);
        } else if (currentPage > 0) {
            loadPage(currentPage - 1, false);
        }
    }

    private Mat bufferedImageToMat(BufferedImage bi) {
        BufferedImage converted = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        converted.getGraphics().drawImage(bi, 0, 0, null);
        byte[] data = ((DataBufferByte) converted.getRaster().getDataBuffer()).getData();
        Mat mat = new Mat(converted.getHeight(), converted.getWidth(), CvType.CV_8UC3);
        mat.put(0, 0, data);
        return mat;
    }

    private BufferedImage matToBufferedImage(Mat mat) {
        int w = mat.cols(), h = mat.rows(), c = mat.channels();
        byte[] source = new byte[w * h * c];
        mat.get(0, 0, source);
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
        System.arraycopy(source, 0, ((DataBufferByte) image.getRaster().getDataBuffer()).getData(), 0, source.length);
        return image;
    }

    private void applyBaroqueBackground(StackPane root) {
        try {
            // Ensure music_stand_bg.jpg is in src/main/resources
            Image standImg = new Image(getClass().getResourceAsStream("/music_stand_bg.jpg"));
            ImageView standView = new ImageView(standImg);
            standView.setPreserveRatio(true);
            standView.fitWidthProperty().bind(root.widthProperty());
            root.getChildren().add(0, standView);
            root.setStyle("-fx-background-color: black;");
        } catch (Exception e) {
            System.err.println("Background image not found.");
        }
    }
}