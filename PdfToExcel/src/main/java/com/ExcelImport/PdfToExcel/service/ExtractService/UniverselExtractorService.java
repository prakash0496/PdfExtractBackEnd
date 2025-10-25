package com.ExcelImport.PdfToExcel.service.ExtractService;



import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.RectangularTextContainer;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;
import technology.tabula.extractors.BasicExtractionAlgorithm;
import technology.tabula.Table;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;


@Service
public class UniverselExtractorService {

    // 🔹 Main entry point
    public List<Map<String, String>> extractAndParsePdf(byte[] pdfBytes) throws Exception {
        // 1️⃣ Try Tabula (table-based PDF)
        List<Map<String, String>> tabulaData = extractUsingTabula(pdfBytes);
        if (!tabulaData.isEmpty()) return tabulaData;

        // 2️⃣ Try PDFTextStripper (digital PDF with text)
        String textData = extractTextFromPdf(pdfBytes);
        if (textData != null && !textData.trim().isEmpty()) {
            return parseTransactions(textData);
        }

        // 3️⃣ Fallback OCR (scanned PDF)
        String ocrData = extractTextFromScannedPdf(pdfBytes);
        return parseTransactions(ocrData);
    }

    // ===========================================================
    // 🔹 Extract text from digital PDF
    // ===========================================================
    public String extractTextFromPdf(byte[] pdfBytes) throws Exception {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            pdfStripper.setSortByPosition(true); // important for tables
            return pdfStripper.getText(document);
        }
    }

    // ===========================================================
    // 🔹 Extract text from scanned PDF (OCR)
    // ===========================================================
    private String extractTextFromScannedPdf(byte[] pdfBytes) throws Exception {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);

            ITesseract tesseract = new Tesseract();
            tesseract.setDatapath("E:/PdfExtract/PdfToExcel/PdfToExcel/src/main/java/com/ExcelImport/PdfToExcel/tessdata");
            tesseract.setLanguage("eng");

            StringBuilder fullText = new StringBuilder();
            for (int page = 0; page < document.getNumberOfPages(); ++page) {
                BufferedImage image = pdfRenderer.renderImageWithDPI(page, 400);
                String extractedText = tesseract.doOCR(image);
                fullText.append(extractedText).append("\n");
            }
            return fullText.toString();
        }
    }

    // ===========================================================
    // 🔹 Extract tables using Tabula
    // ===========================================================
    private List<Map<String, String>> extractUsingTabula(byte[] pdfBytes) {
        List<Map<String, String>> extractedData = new ArrayList<>();

        try (PDDocument pdfDocument = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            ObjectExtractor extractor = new ObjectExtractor(pdfDocument);
            SpreadsheetExtractionAlgorithm spreadsheetExtractor = new SpreadsheetExtractionAlgorithm();
            BasicExtractionAlgorithm basicExtractor = new BasicExtractionAlgorithm();

            for (int i = 1; i <= pdfDocument.getNumberOfPages(); i++) {
                Page page = extractor.extract(i);
                List<Table> tables = spreadsheetExtractor.extract(page);
                if (tables.isEmpty()) tables = basicExtractor.extract(page);

                for (Table table : tables) {
                    List<List<RectangularTextContainer>> rows = table.getRows();
                    if (rows == null || rows.size() <= 1) continue;

                    List<String> headers = rows.get(0).stream()
                            .map(cell -> cell == null ? "" : cell.getText().trim())
                            .map(h -> h.isEmpty() ? "Column" : h)
                            .collect(Collectors.toList());

                    for (int r = 1; r < rows.size(); r++) {
                        List<RectangularTextContainer> cells = rows.get(r);
                        Map<String, String> rowMap = new LinkedHashMap<>();
                        for (int c = 0; c < headers.size(); c++) {
                            String header = headers.get(c);
                            String cellText = "";
                            if (cells != null && c < cells.size() && cells.get(c) != null) {
                                cellText = cells.get(c).getText().trim();
                            }
                            rowMap.put(header, cellText);
                        }

                        boolean hasValue = rowMap.values().stream().anyMatch(v -> v != null && !v.isEmpty());
                        if (hasValue) extractedData.add(rowMap);
                    }
                }
            }
        } catch (Exception e) {
            return Collections.emptyList();
        }

        return extractedData;
    }

    // ===========================================================
    // 🔹 Parse OCR or text-based PDF into structured JSON
    // ===========================================================
    private List<Map<String, String>> parseTransactions(String ocrText) {
        List<Map<String, String>> transactions = new ArrayList<>();
        String[] lines = ocrText.split("\\r?\\n");

        Pattern datePattern = Pattern.compile("\\b\\d{1,2}[-/ ]\\d{1,2}[-/ ]\\d{2,4}\\b");
        StringBuilder currentTransaction = new StringBuilder();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            Matcher matcher = datePattern.matcher(line);
            if (matcher.find() && matcher.start() < 5) {
                if (currentTransaction.length() > 0) {
                    Map<String, String> data = parseRow(currentTransaction.toString());
                    if (!data.isEmpty()) transactions.add(data);
                    currentTransaction.setLength(0);
                }
                currentTransaction.append(line);
            } else if (currentTransaction.length() > 0) {
                currentTransaction.append(" ").append(line);
            }
        }

        if (currentTransaction.length() > 0) {
            Map<String, String> data = parseRow(currentTransaction.toString());
            if (!data.isEmpty()) transactions.add(data);
        }

        return transactions;
    }

    // ===========================================================
    // 🔹 Parse a single transaction line into JSON
    // ===========================================================
    private Map<String, String> parseRow(String row) {
        Map<String, String> map = new LinkedHashMap<>();

        // Date
        Pattern datePattern = Pattern.compile("\\b\\d{1,2}[-/ ]\\d{1,2}[-/ ]\\d{2,4}\\b");
        Matcher dateMatcher = datePattern.matcher(row);
        if (dateMatcher.find()) map.put("Date", dateMatcher.group());

        // Clean row text
        row = row.replaceAll("[|]", " ").replaceAll(" +", " ").replaceAll("[^A-Za-z0-9.,/-]", " ").trim();

        // Amounts
        Pattern amountPattern = Pattern.compile("\\b\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d{1,2})?\\b");
        Matcher amtMatcher = amountPattern.matcher(row);
        List<String> amounts = new ArrayList<>();
        while (amtMatcher.find()) {
            String amt = amtMatcher.group().replaceAll(",", "");
            if (amt.length() > 2 && !amt.contains(".")) amt = amt.substring(0, amt.length() - 2) + "." + amt.substring(amt.length() - 2);
            amounts.add(amt);
        }

        // Debit / Credit / Balance
        if (amounts.size() >= 3) {
            map.put("Withdrawals", amounts.get(amounts.size() - 3));
            map.put("Credits", amounts.get(amounts.size() - 2));
            map.put("Balance", amounts.get(amounts.size() - 1));
        } else if (amounts.size() == 2) {
            String descLower = row.toLowerCase();
            if (descLower.contains("cr") || descLower.contains("credit") || descLower.contains("by"))
                map.put("Credits", amounts.get(0));
            else
                map.put("Withdrawals", amounts.get(0));
            map.put("Balance", amounts.get(1));
        } else if (amounts.size() == 1) map.put("Balance", amounts.get(0));

        // Description cleanup
        String description = row.replaceAll("\\b\\d{1,2}[-/ ]\\d{1,2}[-/ ]\\d{2,4}\\b", "")
                .replaceAll("\\b\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?\\b", "")
                .replaceAll(" +", " ").trim();
        map.put("Description", description);

        return map;
    }

}
