package com.piano.xr;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;
import nu.pattern.OpenCV;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainApp extends Application {
    static { try { OpenCV.loadLocally(); } catch (Throwable e) { OpenCV.loadShared(); } }

    private PDDocument document;
    private PDFRenderer renderer;
    private File currentPdfFile;
    private int currentPage = 0;
    private List<Integer> systemSnaps = new ArrayList<>();
    
    private ScrollPane scrollPane;
    private ImageView pdfImageView = new ImageView();
    private Canvas lineCanvas = new Canvas();
    private Integer selectedLineIndex = null;

    @Override
    public void start(Stage stage) throws Exception {
        String path = getParameters().getRaw().isEmpty() ? null : getParameters().getRaw().get(0);
        if (path == null) return;
        currentPdfFile = new File(path);

        File txtFile = getTxtFile(currentPdfFile);
        if (txtFile.exists()) {
            PerformanceView pv = new PerformanceView(currentPdfFile, txtFile);
            pv.start(new Stage());
            return; 
        }

        setupEditorUI(stage);
        loadNewPDF(currentPdfFile);
    }

    private void setupEditorUI(Stage stage) {
        scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        
        // Use a Pane to layer Canvas on top of ImageView
        Pane container = new Pane(pdfImageView, lineCanvas);
        scrollPane.setContent(container);

        setupMouseEvents(container);

        Scene scene = new Scene(new StackPane(scrollPane));
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.S) saveSnaps();
            if (e.getCode() == KeyCode.ESCAPE) stage.close();
            if (e.getCode() == KeyCode.RIGHT) navigatePage(1);
            if (e.getCode() == KeyCode.LEFT) navigatePage(-1);
        });

        stage.setScene(scene);
        stage.setFullScreen(true);
        stage.show();
    }

    private void setupMouseEvents(Pane container) {
        container.setOnMouseMoved(e -> {
            container.setCursor(isOverLine(e.getY()) ? javafx.scene.Cursor.V_RESIZE : javafx.scene.Cursor.DEFAULT);
        });

        container.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2) {
                toggleSnap((int) e.getY());
            }
        });

        container.setOnMousePressed(e -> {
            selectedLineIndex = findSelectedLineIndex(e.getY());
        });

        container.setOnMouseDragged(e -> {
            if (selectedLineIndex != null) {
                systemSnaps.set(selectedLineIndex, (int) e.getY());
                drawLines(); // Only redraw the canvas, not the image
            }
        });

        container.setOnMouseReleased(e -> {
            if (selectedLineIndex != null) {
                Collections.sort(systemSnaps);
                selectedLineIndex = null;
                drawLines();
            }
        });
    }

    private void loadNewPDF(File file) throws Exception {
        document = PDDocument.load(file);
        renderer = new PDFRenderer(document);
        loadPage(0);
    }

    private void loadPage(int pageIndex) {
        try {
            currentPage = pageIndex;
            BufferedImage bImage = renderer.renderImageWithDPI(pageIndex, 150);
            
            // Auto-detect if fresh page
            if (systemSnaps.isEmpty()) {
                Mat mat = bufferedImageToMat(bImage);
                systemSnaps = findSystemsViaNeighborhood(mat);
                mat.release();
            }

            Image fxImage = SwingFXUtils.toFXImage(bImage, null);
            pdfImageView.setImage(fxImage);
            
            lineCanvas.setWidth(fxImage.getWidth());
            lineCanvas.setHeight(fxImage.getHeight());
            
            drawLines();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void drawLines() {
        GraphicsContext gc = lineCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, lineCanvas.getWidth(), lineCanvas.getHeight());
        gc.setStroke(Color.BLUE);
        gc.setLineWidth(4);
        for (Integer y : systemSnaps) {
            gc.strokeLine(0, y, lineCanvas.getWidth(), y);
        }
    }

    private void toggleSnap(double y) {
        Integer found = findSelectedLineIndex(y);
        if (found != null) systemSnaps.remove((int)found);
        else systemSnaps.add((int)y);
        Collections.sort(systemSnaps);
        drawLines();
    }

    private boolean isOverLine(double y) {
        return findSelectedLineIndex(y) != null;
    }

    private Integer findSelectedLineIndex(double y) {
        for (int i = 0; i < systemSnaps.size(); i++) {
            if (Math.abs(systemSnaps.get(i) - y) < 20) return i;
        }
        return null;
    }

    private void navigatePage(int dir) {
        int next = currentPage + dir;
        if (next >= 0 && next < document.getNumberOfPages()) {
            systemSnaps.clear(); // Clear for new page detection
            loadPage(next);
        }
    }

    private void saveSnaps() {
        try (PrintWriter out = new PrintWriter(getTxtFile(currentPdfFile))) {
            for (Integer s : systemSnaps) out.println(s);
            System.out.println("Saved Mapping!");
        } catch (Exception e) { e.printStackTrace(); }
    }

    private File getTxtFile(File pdf) {
        return new File(pdf.getAbsolutePath().replace(".pdf", ".txt"));
    }

    // --- Utility Methods (OpenCV & Processing) ---

    private List<Integer> findSystemsViaNeighborhood(Mat source) {
        List<Integer> snaps = new ArrayList<>();
        snaps.add(10); 
        Mat gray = new Mat(); Mat thresh = new Mat();
        Imgproc.cvtColor(source, gray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.threshold(gray, thresh, 200, 255, Imgproc.THRESH_BINARY_INV);
        int rows = thresh.rows(); int cols = thresh.cols();
        List<Double> densities = new ArrayList<>();
        for (int y = 0; y < rows - 5; y += 5) {
            double bandSum = 0;
            for (int i = 0; i < 5; i++) {
                double rowSum = 0;
                for (int x = 0; x < cols; x++) if (thresh.get(y + i, x)[0] > 0) rowSum++;
                bandSum += (rowSum / cols);
            }
            densities.add(bandSum / 5);
        }
        int searchRadius = 50; 
        for (int i = (int)(rows*0.1/5); i < densities.size() - (int)(rows*0.1/5); i++) {
            double currentDensity = densities.get(i); boolean isLocalMin = true;
            for (int j = Math.max(0, i - searchRadius); j < Math.min(densities.size(), i + searchRadius); j++) {
                if (densities.get(j) < currentDensity) { isLocalMin = false; break; }
            }
            if (isLocalMin && currentDensity < 0.05) {
                int snapY = i * 5;
                if (snaps.isEmpty() || snapY - snaps.get(snaps.size()-1) > 250) snaps.add(snapY);
            }
        }
        gray.release(); thresh.release();
        return snaps;
    }

    private Mat bufferedImageToMat(BufferedImage bi) {
        BufferedImage converted = new BufferedImage(bi.getWidth(), bi.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        converted.getGraphics().drawImage(bi, 0, 0, null);
        byte[] data = ((DataBufferByte) converted.getRaster().getDataBuffer()).getData();
        Mat mat = new Mat(converted.getHeight(), converted.getWidth(), CvType.CV_8UC3);
        mat.put(0, 0, data);
        return mat;
    }
}