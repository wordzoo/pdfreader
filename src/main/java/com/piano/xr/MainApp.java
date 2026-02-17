package com.piano.xr;

import org.apache.pdfbox.pdmodel.PDDocument;
import java.io.File;

public class MainApp {
    public static void main(String[] args) {
        System.out.println("Piano Stave Reader Starting...");
        
        if (args.length < 1) {
            System.out.println("Usage: java -jar stave-reader.jar <path-to-pdf>");
            return;
        }

        try (PDDocument document = PDDocument.load(new File(args[0]))) {
            int pageCount = document.getNumberOfPages();
            System.out.println("Successfully loaded PDF with " + pageCount + " pages.");
            // Next step: rendering page 1 to an image for analysis
        } catch (Exception e) {
            System.err.println("Error loading PDF: " + e.getMessage());
        }
    }
}