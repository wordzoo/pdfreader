package com.piano.xr;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainApp {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java -jar stave-reader.jar <path-to-pdf>");
            return;
        }

        try (PDDocument document = PDDocument.load(new File(args[0]))) {
            PDFRenderer renderer = new PDFRenderer(document);
            
            // Analyze the first page (index 0)
            BufferedImage image = renderer.renderImageWithDPI(0, 150); // 150 DPI is plenty for analysis
            List<Integer> systemTops = findSystems(image);

            System.out.println("Analysis complete for Page 1:");
            for (int i = 0; i < systemTops.size(); i++) {
                System.out.println("System " + (i + 1) + " starts at Y-pixel: " + systemTops.get(i));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<Integer> findSystems(BufferedImage image) {
        List<Integer> tops = new ArrayList<>();
        int width = image.getWidth();
        int height = image.getHeight();
        
        // The "Bridge": How many pixels of white space to ignore before 
        // declaring a system "finished"? (e.g., 40 pixels at 150 DPI)
        int minGapHeight = 40; 
        int whiteSpaceCounter = 0;
        boolean inSystem = false;

        for (int y = 0; y < height; y++) {
            int blackPixels = 0;
            for (int x = 0; x < width; x++) {
                if ((image.getRGB(x, y) & 0xFF) < 128) blackPixels++;
            }

            double density = (double) blackPixels / width;

            if (density > 0.01) { // Found black pixels
                if (!inSystem) {
                    tops.add(y);
                    inSystem = true;
                }
                whiteSpaceCounter = 0; // Reset gap counter
            } else { // Found white space
                if (inSystem) {
                    whiteSpaceCounter++;
                    if (whiteSpaceCounter > minGapHeight) {
                        inSystem = false;
                    }
                }
            }
        }
        return tops;
    }
}