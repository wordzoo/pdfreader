package com.piano.xr;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import nu.pattern.OpenCV;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainApp extends Application {
    
    // STEP 1: Viewport Height Configuration
    // private final double VIEWPORT_HEIGHT_RATIO = 3.0; // Old height (1/3 screen)
    private final double VIEWPORT_HEIGHT_RATIO = 1.0;    // New height (Full screen)

    static { 
        try { OpenCV.loadLocally(); } catch (Throwable e) { OpenCV.loadShared(); }
    }

    private PDDocument document;
    private PDFRenderer renderer;
    private int currentPage = -1;
    private int currentSystemIndex = 0;
    private List<Integer> systemTops = new ArrayList<>();

    private ScrollPane scrollPane;
    private ImageView pdfImageView;

    @Override
    public void start(Stage stage) throws Exception {
        StackPane rootLayout = new StackPane();
        scrollPane = new ScrollPane();
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color:transparent; -fx-background: transparent;");

        double screenHeight = Screen.getPrimary().getBounds().getHeight();
        scrollPane.setMaxHeight(screenHeight / VIEWPORT_HEIGHT_RATIO);
        scrollPane.setPrefHeight(screenHeight / VIEWPORT_HEIGHT_RATIO);

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
            // 150 DPI provides a good balance between detail and processing speed
            BufferedImage bImage = renderer.renderImageWithDPI(pageIndex, 150);
            
            Mat source = bufferedImageToMat(bImage);
            systemTops = findSystemsViaHough(source);
            
            if (systemTops.isEmpty()) {
                systemTops.add(100); 
            }
            
            System.out.println("Page " + pageIndex + ": Detected " + systemTops.size() + " systems.");

            pdfImageView = new ImageView(SwingFXUtils.toFXImage(matToBufferedImage(source), null));
            pdfImageView.setPreserveRatio(true);
            pdfImageView.setFitWidth(Screen.getPrimary().getBounds().getWidth() - 400);

            VBox wrapper = new VBox(pdfImageView);
            wrapper.setAlignment(Pos.CENTER);
            // Large bottom padding allows the last system on the page to be scrolled to the top
            wrapper.setPadding(new Insets(0, 0, Screen.getPrimary().getBounds().getHeight(), 0));
            
            scrollPane.setContent(wrapper);
            currentSystemIndex = jumpToTop ? 0 : systemTops.size() - 1;
            
            Platform.runLater(() -> scrollToSystem(currentSystemIndex));
            source.release();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private List<Integer> findSystemsViaNeighborhood(Mat source) {
        List<Integer> snaps = new ArrayList<>();
        
        // EXPLICIT TOP SNAP: Always start at the top of the page
        snaps.add(10); 

        Mat gray = new Mat();
        Mat thresh = new Mat();
        Imgproc.cvtColor(source, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.threshold(gray, thresh, 200, 255, Imgproc.THRESH_BINARY_INV);

        int rows = thresh.rows();
        int cols = thresh.cols();
        int bandSize = 5;
        
        // Generate Density Profile
        List<Double> densities = new ArrayList<>();
        for (int y = 0; y < rows - bandSize; y += bandSize) {
            double bandSum = 0;
            for (int i = 0; i < bandSize; i++) {
                double rowSum = 0;
                for (int x = 0; x < cols; x++) {
                    if (thresh.get(y + i, x)[0] > 0) rowSum++;
                }
                bandSum += (rowSum / cols);
            }
            densities.add(bandSum / bandSize);
        }

        // Neighborhood Search for Troughs
        int searchRadius = 50; 
        int ignoreBands = (int)((rows * 0.10) / bandSize);

        for (int i = ignoreBands; i < densities.size() - ignoreBands; i++) {
            double currentDensity = densities.get(i);
            boolean isLocalMin = true;

            for (int j = Math.max(0, i - searchRadius); j < Math.min(densities.size(), i + searchRadius); j++) {
                if (densities.get(j) < currentDensity) {
                    isLocalMin = false;
                    break;
                }
            }

            // Snap to the center of the whitest troughs
            if (isLocalMin && currentDensity < 0.05) {
                int snapY = i * bandSize;
                // Ensure we aren't doubling up on the top snap or previous markers
                if (snapY - snaps.get(snaps.size()-1) > 250) {
                    snaps.add(snapY);
                }
            }
        }

        // DRAWING: Only blue lines in the troughs
        for (Integer yPos : snaps) {
            Imgproc.line(source, new Point(0, yPos), new Point(cols, yPos), new Scalar(255, 0, 0), 4);
        }

        gray.release(); thresh.release();
        return snaps;
    }

    private void scrollToSystem(int index) {
        if (scrollPane.getContent() == null || systemTops.isEmpty()) return;
        
        double vH = scrollPane.getHeight();
        double cH = scrollPane.getContent().getBoundsInLocal().getHeight();
        double targetY = systemTops.get(index);
        
        // Offset to keep the system slightly below the top of the viewport
        double offset = vH * 0.10;
        double scrollPos = (targetY - offset) / (cH - vH);
        
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
}