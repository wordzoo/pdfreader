package com.piano.xr;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.util.Base64;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;

public class GeminiVisionService {
    private final String apiKey = System.getenv("GEMINI_API_KEY");
 // Change gemini-1.5-flash to gemini-2.5-flash
    private final String endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey;

    public String analyzePage(BufferedImage image) throws Exception {
        // (Existing logic...)
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", baos);
        String base64Image = Base64.getEncoder().encodeToString(baos.toByteArray());

        String jsonPayload = "{"
            + "\"contents\":[{\"parts\":[{\"text\":\"Detect all musical systems in this page. Return only a JSON object with a key 'systems' containing a list of integers representing the starting Y-pixel coordinates.\"},"
            + "{\"inline_data\":{\"mime_type\":\"image/png\",\"data\":\"" + base64Image + "\"}}]}], "
            + "\"generationConfig\":{\"response_mime_type\":\"application/json\"}}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(endpoint))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
            .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }

    // --- DEBUG ENTRY POINT ---
    public static void main(String[] args) {
        try {
            System.out.println("Starting debug: Testing Gemini API...");
            GeminiVisionService service = new GeminiVisionService();
            
            // Point this to a test image on your machine
            File testFile = new File("c:\\temp\\pdfs\\BWV 915.pdf");
            if (!testFile.exists()) {
                System.out.println("File not found: " + testFile.getAbsolutePath());
                return;
            }

            // Render the first page of the PDF to a BufferedImage
            try (PDDocument document = PDDocument.load(testFile)) {
                PDFRenderer renderer = new PDFRenderer(document);
                BufferedImage bImage = renderer.renderImageWithDPI(0, 150); // 150 DPI
                
                // Now pass this to your service
                
                String response = service.analyzePage(bImage);
                
                System.out.println("API Response: " + response);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}