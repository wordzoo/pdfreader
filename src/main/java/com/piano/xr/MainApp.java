package com.piano.xr;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
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
import java.util.*;

public class MainApp extends Application {
    static { try { OpenCV.loadLocally(); } catch (Throwable e) { OpenCV.loadShared(); } }

    private PDDocument document;
    private PDFRenderer renderer;
    private File currentPdfFile;
    private int currentPage = 0;
    
    private Map<Integer, List<Integer>> allPagesSnaps = new HashMap<>();
    
    private ScrollPane scrollPane;
    private ImageView pdfImageView = new ImageView();
    private Canvas lineCanvas = new Canvas();
    private Integer selectedLineIndex = null;
    private Pane container;
    private Label pageLabel = new Label();

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
        StackPane root = new StackPane();
        scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        
        container = new Pane(pdfImageView, lineCanvas);
        scrollPane.setContent(container);

        // TOP CONTROLS (Save/Exit)
        HBox topControls = new HBox(15);
        topControls.setPadding(new Insets(20));
        topControls.setPickOnBounds(false);

        Button saveBtn = new Button("SAVE ALL");
        saveBtn.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold;");
        saveBtn.setOnAction(e -> saveSnaps());

        Button exitBtn = new Button("EXIT");
        exitBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
        exitBtn.setOnAction(e -> stage.close());

        topControls.getChildren().addAll(saveBtn, exitBtn);

        // BOTTOM CONTROLS (Paging)
        HBox bottomControls = new HBox(20);
        bottomControls.setPadding(new Insets(20));
        bottomControls.setAlignment(Pos.CENTER);
        bottomControls.setPickOnBounds(false);
        bottomControls.setStyle("-fx-background-color: rgba(0,0,0,0.5); -fx-background-radius: 10;");
        bottomControls.setMaxWidth(300);
        bottomControls.setMaxHeight(60);

        Button prevBtn = new Button("< PREV");
        prevBtn.setStyle("-fx-font-weight: bold;");
        prevBtn.setOnAction(e -> navigatePage(-1));

        Button nextBtn = new Button("NEXT >");
        nextBtn.setStyle("-fx-font-weight: bold;");
        nextBtn.setOnAction(e -> navigatePage(1));

        pageLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
        
        bottomControls.getChildren().addAll(prevBtn, pageLabel, nextBtn);
        
        root.getChildren().addAll(scrollPane, topControls, bottomControls);
        StackPane.setAlignment(topControls, Pos.TOP_LEFT);
        StackPane.setAlignment(bottomControls, Pos.BOTTOM_CENTER);
        StackPane.setMargin(bottomControls, new Insets(0, 0, 40, 0));

        setupMouseEvents(container);

        Scene scene = new Scene(root);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.RIGHT) navigatePage(1);
            if (e.getCode() == KeyCode.LEFT) navigatePage(-1);
            if (e.getCode() == KeyCode.S) saveSnaps();
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
                toggleSnap(e.getY());
            }
        });

        container.setOnMousePressed(e -> selectedLineIndex = findSelectedLineIndex(e.getY()));

        container.setOnMouseDragged(e -> {
            if (selectedLineIndex != null) {
                allPagesSnaps.get(currentPage).set(selectedLineIndex, (int) e.getY());
                drawLines();
            }
        });

        container.setOnMouseReleased(e -> {
            if (selectedLineIndex != null) {
                Collections.sort(allPagesSnaps.get(currentPage));
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
            pageLabel.setText("Page " + (currentPage + 1) + " / " + document.getNumberOfPages());
            
            BufferedImage bImage = renderer.renderImageWithDPI(pageIndex, 150);
            
            if (!allPagesSnaps.containsKey(currentPage)) {
                Mat mat = bufferedImageToMat(bImage);
                allPagesSnaps.put(currentPage, findSystemsViaNeighborhood(mat));
                mat.release();
            }

            Image fxImage = SwingFXUtils.toFXImage(bImage, null);
            pdfImageView.setImage(fxImage);
            lineCanvas.setWidth(fxImage.getWidth());
            lineCanvas.setHeight(fxImage.getHeight());
            
            drawLines();
            scrollPane.setVvalue(0);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void drawLines() {
        GraphicsContext gc = lineCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, lineCanvas.getWidth(), lineCanvas.getHeight());
        gc.setStroke(Color.BLUE);
        gc.setLineWidth(4);
        List<Integer> currentSnaps = allPagesSnaps.get(currentPage);
        if (currentSnaps != null) {
            for (Integer y : currentSnaps) gc.strokeLine(0, y, lineCanvas.getWidth(), y);
        }
    }

    private void toggleSnap(double y) {
        List<Integer> snaps = allPagesSnaps.get(currentPage);
        Integer found = findSelectedLineIndex(y);
        if (found != null) snaps.remove((int)found);
        else snaps.add((int)y);
        Collections.sort(snaps);
        drawLines();
    }

    private Integer findSelectedLineIndex(double y) {
        List<Integer> snaps = allPagesSnaps.get(currentPage);
        if (snaps == null) return null;
        for (int i = 0; i < snaps.size(); i++) {
            if (Math.abs(snaps.get(i) - y) < 20) return i;
        }
        return null;
    }

    private boolean isOverLine(double y) { return findSelectedLineIndex(y) != null; }

    private void navigatePage(int dir) {
        int next = currentPage + dir;
        if (next >= 0 && next < document.getNumberOfPages()) {
            loadPage(next);
        }
    }

 // Replace the saveSnaps method in MainApp.java with this:
    private void saveSnaps() {
        File txtFile = getTxtFile(currentPdfFile);
        try (PrintWriter out = new PrintWriter(txtFile)) {
            // 1. Sort the pages so the performance follows the book order
            List<Integer> sortedPages = new ArrayList<>(allPagesSnaps.keySet());
            Collections.sort(sortedPages);

            for (Integer p : sortedPages) {
                List<Integer> snaps = allPagesSnaps.get(p);
                if (snaps != null) {
                    // 2. Sort the Y-coordinates within each page
                    Collections.sort(snaps);
                    for (Integer s : snaps) {
                        out.println(p + ":" + s); 
                    }
                }
            }
            
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setHeaderText("Data Refined & Saved");
            alert.setContentText("Mapping saved in chronological page:y format.");
            alert.showAndWait();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private File getTxtFile(File pdf) {
        String path = pdf.getAbsolutePath();
        return new File(path.substring(0, path.lastIndexOf('.')) + ".txt");
    }

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