package com.piano.xr;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.control.Label;
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
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
// Helper to track page/y pairing
class SnapPoint {
	int page;
	int y;

	SnapPoint(int p, int y) {
		this.page = p;
		this.y = y;
	}
}

public class PerformanceView extends Application {
	public static final boolean DEBUG = true;
	private static final int FLUSH_TOP_OFFSET = 30;

	@FXML private ImageView bgImageView;
    @FXML private ImageView pdfImageView;
    @FXML private Pane musicClipPane;

	private PDDocument document; // Keep alive as class field
	private PDFRenderer renderer;
	private List<SnapPoint> yPositions = new ArrayList<>();
	private int currentIndex = 0;
	private int currentLoadedPage = -1;

	// Class-level field makes it visible to both start() and analyzePdf()
	private Task<List<SnapPoint>> analysisTask;
	private Label statusLabel = new Label("Waiting to start...");

	public static void main(String[] args) {
		launch(args);
	}

	@Override
	public void start(Stage stage) throws Exception {
		// 1. Create the loader
	    FXMLLoader loader = new FXMLLoader(getClass().getResource("/performance_view.fxml"));
	    
	    // 2. Set THIS class instance as the controller!
	    // This forces the FXML loader to inject fields into YOUR current object
	    loader.setController(this);
	    StackPane root = loader.load();
	    
	    // 2. Setup Background Image
	    Image bgImg = new Image(getClass().getResourceAsStream("/music_stand_bg.jpg"));
	    double renderedHeight = Screen.getPrimary().getBounds().getHeight() / 4.0;
	    double aspectRatio = bgImg.getWidth() / bgImg.getHeight();
	    double renderedWidth = renderedHeight * aspectRatio;
	    
	    bgImageView.setImage(bgImg);
	    bgImageView.setFitWidth(renderedWidth);
	    bgImageView.setFitHeight(renderedHeight);
	    bgImageView.setPreserveRatio(true);

	    // 3. File Selection
	    File pdfFile = DEBUG ? 
	        new File("C:\\Users\\thoma\\eclipse-workspace\\stave-reader\\test_data\\BWV 806.pdf") : 
	        new FileChooser().showOpenDialog(stage);

	    if (pdfFile == null || !pdfFile.exists()) {
	        stage.close();
	        return;
	    }

	    this.document = PDDocument.load(pdfFile);
	    this.renderer = new PDFRenderer(document);

	    // 4. Scene Setup & Window sizing
	    stage.setWidth(renderedWidth);
	    stage.setHeight(renderedHeight);
	    stage.setResizable(false);
	    
	    Scene scene = new Scene(root, renderedWidth, renderedHeight);
	    stage.setScene(scene);

	    // 5. Navigation
	    scene.setOnKeyPressed(event -> {
	        if (event.getCode() == KeyCode.DOWN && currentIndex < yPositions.size() - 1) loadSnap(++currentIndex);
	        else if (event.getCode() == KeyCode.UP && currentIndex > 0) loadSnap(--currentIndex);
	    });

	    // 6. UI constraints
	    musicClipPane.setMaxWidth(renderedWidth * 0.9);
	    StackPane.setAlignment(musicClipPane, javafx.geometry.Pos.CENTER);

	    // 7. Status Label (removed redundant text setting)
	    Label statusLabel = new Label("Initializing...");
	    statusLabel.setStyle("-fx-text-fill: white; -fx-font-size: 20px; -fx-background-color: rgba(0,0,0,0.6); -fx-padding: 10;");
	    root.getChildren().add(statusLabel);

	    stage.show();

	    // 8. Start Analysis
	    analyzePdf(document);
	}
	public static class SystemData {
	    public int page;
	    public int y_start;
	}
	private List<SnapPoint> getListFromJsonFile(File jsonFile) {
	    List<SnapPoint> points = new ArrayList<>();
	    try {
	        ObjectMapper mapper = new ObjectMapper();
	        
	        // Define a wrapper if your JSON structure has a "systems" root key
	        // If your JSON is just the list, you can map directly. 
	        // Based on the format requested, it is a list inside a "systems" key.
	        JsonNode root = mapper.readTree(jsonFile);
	        JsonNode systemsNode = root.get("systems");
	        
	        // Deserialize the array of objects
	        List<SystemData> dataList = mapper.readValue(
	            systemsNode.toString(), 
	            new TypeReference<List<SystemData>>() {}
	        );
	        
	        // Convert to your existing SnapPoint format
	        for (SystemData sd : dataList) {
	            points.add(new SnapPoint(sd.page, sd.y_start));
	        }
	    } catch (Exception e) {
	        System.err.println("Failed to parse JSON: " + e.getMessage());
	        e.printStackTrace();
	    }
	    return points;
	}
	private void analyzePdf(PDDocument document) {
		if (DEBUG) {
	        // Correctly assigning the result of the parser
	        this.yPositions = getListFromJsonFile(new File("C:\\Users\\thoma\\eclipse-workspace\\stave-reader\\test_data\\BWV 806.json"));
	        
	        // Trigger initial load since the task is bypassed
	        if (!yPositions.isEmpty()) {
	            loadSnap(0);
	        }
	        return;
	    }
			
		this.analysisTask = new Task<List<SnapPoint>>() {
			@Override
			protected List<SnapPoint> call() throws Exception {
				List<SnapPoint> combined = new ArrayList<>();
				ObjectMapper mapper = new ObjectMapper();

				for (int i = 0; i < document.getNumberOfPages(); i++) {
					updateMessage("Analyzing page " + (i + 1) + " of " + document.getNumberOfPages() + "...");

					BufferedImage bImage = renderer.renderImageWithDPI(i, 150);
					String json = new GeminiVisionService().analyzePage(bImage);

					// Check if API returned an error JSON
					JsonNode root = mapper.readTree(json);
					if (root.has("error")) {
						String status = root.at("/error/status").asText();
						if ("RESOURCE_EXHAUSTED".equals(status)) {
							throw new Exception(
									"Quota exceeded! You've reached your free daily limit (20 requests). Please wait 24 hours or upgrade your billing plan.");
						}
						throw new Exception("API Error: " + root.at("/error/message").asText());
					}

					// Proceed with normal parsing
					String clean = json.replaceAll("```json", "").replaceAll("```", "").trim();
					JsonNode systems = root.at("/candidates/0/content/parts/0/text");

					List<Integer> pageY = mapper.readValue(mapper.readTree(systems.asText()).get("systems").toString(),
							new TypeReference<List<Integer>>() {
							});
					for (Integer y : pageY)
						combined.add(new SnapPoint(i, y));

					// Add a small delay to avoid hitting limits if you have a larger plan
					Thread.sleep(2000);
				}
				return combined;
			}
		};

		statusLabel.textProperty().bind(analysisTask.messageProperty());

		analysisTask.setOnSucceeded(e -> {
			this.yPositions = analysisTask.getValue();
			statusLabel.setVisible(false);
			if (!yPositions.isEmpty())
				loadSnap(0);
		});

		analysisTask.setOnFailed(e -> {
			Throwable ex = analysisTask.getException();
			System.err.println("Task failed: " + ex.getMessage());
			Platform.runLater(() -> {
				statusLabel.setText("Error: " + ex.getMessage());
				statusLabel.setStyle("-fx-text-fill: red; -fx-background-color: black;");
			});
		});

		new Thread(this.analysisTask).start();
	}
	private void loadSnap(int index) {
	    SnapPoint point = yPositions.get(index);
	    int dpi = 150;
	    int standardPadding = (int) (1.0 * dpi / 2.54); 

	    try {
	        BufferedImage fullPage = renderer.renderImageWithDPI(point.page, dpi);
	        
	        // 1. Dynamic Padding: If we are close to the top, use less padding to trim white space.
	        // If system starts within 50 pixels of the top, limit padding to 10 pixels.
	        int dynamicPadding = (point.y < 50) ? 10 : standardPadding;

	        int systemBottom = point.y + 200; 
	        int cropHeight = Math.min(systemBottom - point.y + (2 * standardPadding), fullPage.getHeight() - point.y);
	        int cropY = Math.max(0, point.y - dynamicPadding);
	        
	        BufferedImage cropped = fullPage.getSubimage(0, cropY, fullPage.getWidth(), cropHeight);

	        Platform.runLater(() -> {
	            pdfImageView.setImage(SwingFXUtils.toFXImage(cropped, null));
	            pdfImageView.setPreserveRatio(true);
	            // Ensure width binding is active
	            pdfImageView.fitWidthProperty().bind(musicClipPane.widthProperty().multiply(0.9));
	        });
	    } catch (Exception e) {
	        System.err.println("Rendering error at index " + index + ": " + e.getMessage());
	    }
	}
}