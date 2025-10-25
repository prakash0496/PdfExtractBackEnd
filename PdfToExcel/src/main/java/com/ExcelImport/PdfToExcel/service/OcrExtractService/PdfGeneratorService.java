package com.ExcelImport.PdfToExcel.service.OcrExtractService;


import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.*;
import lombok.extern.log4j.Log4j2;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.util.*;


@Log4j2
@Service
public class PdfGeneratorService {

    /**
     * Converts an unstructured PDF into a visually structured PDF table.
     * Each line becomes one row, split into multiple columns by spaces.
     */
    public String createColumnTablePdf(File inputPdf, String outputPath) throws Exception {
        // Step 1: Read original PDF text line by line
        StringBuilder pdfText = new StringBuilder();
        try (PDDocument document = PDDocument.load(inputPdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            pdfText.append(stripper.getText(document));
        }

        // Step 2: Split by lines
        String[] lines = pdfText.toString().split("\\r?\\n");

        // Step 3: Create structured PDF with columns
        Document doc = new Document(PageSize.A4.rotate()); // Landscape for better width
        PdfWriter.getInstance(doc, new FileOutputStream(outputPath));
        doc.open();

        Paragraph title = new Paragraph("Structured Table PDF (Auto-Columns)",
                new Font(Font.HELVETICA, 14, Font.BOLD));
        title.setAlignment(Element.ALIGN_CENTER);
        doc.add(title);
        doc.add(Chunk.NEWLINE);

        // Define table with 5 columns (Date, Description, Debit, Credit, Balance)
        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setSpacingBefore(10f);
        table.setSpacingAfter(10f);
        table.setWidths(new float[]{2f, 6f, 2f, 2f, 2f});

        Font headerFont = new Font(Font.HELVETICA, 12, Font.BOLD);
        Font textFont = new Font(Font.HELVETICA, 9);

        // Add headers
        String[] headers = {"Date", "Description", "Debit", "Credit", "Balance"};
        for (String header : headers) {
            PdfPCell headerCell = new PdfPCell(new Phrase(header, headerFont));
            headerCell.setBackgroundColor(Color.LIGHT_GRAY);
            headerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            headerCell.setPadding(6);
            table.addCell(headerCell);
        }

        // Step 4: Fill rows
        int rowCount = 0;
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            rowCount++;

            // Split line by multiple spaces or tabs
            String[] parts = line.trim().split("\\s{2,}|\t");

            // Fill up to 5 columns, rest ignored, empty ones filled as ""
            for (int i = 0; i < 5; i++) {
                String value = (i < parts.length) ? parts[i] : "";
                PdfPCell cell = new PdfPCell(new Phrase(value, textFont));
                cell.setPadding(4);
                cell.setBorderColor(Color.GRAY);
                cell.setBorderWidth(0.5f);
                if (rowCount % 2 == 0)
                    cell.setBackgroundColor(new Color(245, 245, 245));
                table.addCell(cell);
            }
        }

        doc.add(table);
        doc.close();

        System.out.println("✅ Table-based PDF created successfully at: " + outputPath);
        return outputPath;
    }
}