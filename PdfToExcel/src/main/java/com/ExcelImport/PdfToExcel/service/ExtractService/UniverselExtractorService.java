
package com.ExcelImport.PdfToExcel.service.ExtractService;



import com.ExcelImport.PdfToExcel.dto.Response.TransactionDTO;
import com.ExcelImport.PdfToExcel.service.MainExtractService.OcrExtractService;
import com.ExcelImport.PdfToExcel.service.MainExtractService.TextBasedExtractorService;
import com.ExcelImport.PdfToExcel.service.MainExtractService.TabulaExtractorService;
import lombok.extern.log4j.Log4j2;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.formula.atp.Switch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.RectangularTextContainer;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;
import technology.tabula.Table;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

@Log4j2
@Service
public class UniverselExtractorService {


    @Autowired
    private TabulaExtractorService tabulaExtractorService;

    @Autowired
    private FederalBankStatementService federalBankStatementService;

    @Autowired
    private TextBasedExtractorService textBasedExtractorService;

    @Autowired
    private OcrExtractService ocrExtractService;

    @Autowired
    private ICICIBankStatementService iciciBankStatementService;



    // ===========================================================
// 🔹 Main entry point – Hybrid Universal Extractor (Smart Fallback)
// ===========================================================
    public List<TransactionDTO> extractAndParsePdf(MultipartFile pdfBytes, String bank,String password,String accountType) throws Exception {
        log.info("🚀 Starting extraction for bank: {}", bank.toUpperCase());
        log.info("📦 PDF size: {} bytes", pdfBytes != null ? pdfBytes.getSize() : 0);

        // ===========================================================
        // 1️⃣ Detect PDF Type
        // ===========================================================
        assert pdfBytes != null;
        boolean isDigital = isDigitalPdf(pdfBytes.getBytes(),password);
        boolean isTable = hasTransactionTableLayout(pdfBytes.getBytes(),password);

        log.info("📄 PDF Type Detected → {}", isDigital ? "Digital Text-Based" : "Possibly Scanned (Image-based)");
        log.info("📊 Table Structure Detected → {}", isTable ? "Table-Based" : "No Table Structure");


        List<TransactionDTO> transactions = new ArrayList<>();

        // ===========================================================
        // 2️⃣ If Table → Use Tabula Extraction
        // ===========================================================
        if (isTable) {
            log.info("🔹 Table structure detected — attempting Tabula extraction...");
            List<List<String>> rawTable = tabulaExtractorService.extractTableFromPdf(pdfBytes.getBytes(),password);

            switch (bank.toUpperCase()) {
                case "CANARA":
                    transactions = tabulaExtractorService.CanaraBankMapDto(rawTable);
                    break;
                case "SBI":
                    transactions = tabulaExtractorService.statebankMapDto(rawTable);
                    break;
                case "CITY_UNION":
                    transactions = tabulaExtractorService.cityUnionBankMapDto(rawTable);
                    break;
                case "FEDERAL":
                    List<List<String>> federalExtract = tabulaExtractorService.extractTableFederal(pdfBytes.getBytes(),password);
                    transactions = tabulaExtractorService.FederalBankMapDto(federalExtract);
                    break;
                case "ICICI":
                    transactions = tabulaExtractorService.extractUsingTabula(pdfBytes.getBytes());
                    break;
//                case "INDUSLND":
//                    transactions = tabulaExtractorService.extractUsingTabula(pdfBytes.getBytes());
//                    break;
                default:
                    throw new IllegalArgumentException("❌ Unsupported bank: " + bank);
            }

            if (!transactions.isEmpty()) {
                log.info("✅ Tabula extracted {} structured rows for bank: {}", transactions.size(), bank);
                return transactions;
            }
            log.warn("⚠️ Tabula extraction returned no transactions — trying text/hybrid extraction...");
        }

        // ===========================================================
        // 3️⃣ If Digital Text-Based → Use Text Extraction
        // ===========================================================
        if (isDigital && !isTable) {
            log.info("📜 Detected digital text-based PDF — using text extraction...");
            String textData = extractTextFromPdf(pdfBytes.getBytes(),password);
            switch (bank.toUpperCase()){
                case "CANARA":
                    transactions = textBasedExtractorService.parseCanaraBankTransactions(textData);
                    break;
                case "INDIAN_BANK":
                    transactions = textBasedExtractorService.indianBankTransactions(textData);
                    break;
                case "ICICI":
                    if ("SAVING".equalsIgnoreCase(accountType)) {
                        // 🟢 Savings account extraction
                        transactions = textBasedExtractorService.extractICICI(pdfBytes.getBytes());
                    } else {
                        // 🔵 Current account extraction
                        transactions = textBasedExtractorService.extractUsingTabula(pdfBytes.getBytes());
                    }
                    break;
//                default:
//                    transactions = textBasedExtractorService.parseUniversalTransactions(textData);
            }

            if (!transactions.isEmpty()) {
                log.info("✅ Successfully parsed {} transactions from text content.", transactions.size());
                return transactions;
            }
            log.warn("⚠️ Text-based extraction returned no results — checking for OCR pages...");
        }

        // ===========================================================
        // 5️⃣ Fallback → Full OCR Extraction
        // ===========================================================
        log.warn("⚠️ Falling back to full OCR extraction (scanned PDF)...");
        String ocrData = ocrExtractService.extractTextFromScannedPdf(pdfBytes.getBytes(),password);
        switch (bank.toUpperCase()) {
            case "KVB":
            transactions = ocrExtractService.extractTransactions(ocrData);
            break;
            case "INDUSLND":
                transactions = ocrExtractService.extractTransactions(pdfBytes.getBytes());
                break;

            default:
                transactions = ocrExtractService.ocrBasedTransactions(ocrData);
        }
        if (!transactions.isEmpty()) {
            log.info("✅ OCR extraction successful — {} transactions extracted.", transactions.size());
            return transactions;
        }

        // ===========================================================
        // 6️⃣ Final Fallback
        // ===========================================================
        log.error("❌ Extraction failed — no valid data found for bank: {}", bank);
        return transactions;
    }



    // ===========================================================
    // 🔹 Extract text from digital PDF
    // ===========================================================
    public String extractTextFromPdf(byte[] pdfBytes,String password) throws Exception {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes),password, MemoryUsageSetting.setupTempFileOnly())) {

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(document.getNumberOfPages());

            // Step 1️⃣: Raw text extraction
            String rawText = stripper.getText(document);

            // Step 2️⃣: Normalize spacing and clean up layout
            String normalized = rawText
                    .replaceAll("[ \\t]+", " ")     // replace multiple spaces/tabs
                    .replaceAll("\\r", "")          // remove carriage returns
                    .replaceAll("\\n{2,}", "\n")    // collapse multiple newlines
                    .trim();

            // Step 3️⃣: Intelligent filtering of unwanted lines
            StringBuilder sb = new StringBuilder();
            String[] lines = normalized.split("\\n");

            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;

                // Skip generic headers/footers/disclaimers (no bank name hardcoded)
                if (trimmed.matches("(?i).*page\\s*\\d+\\s*(of)?\\s*\\d+.*")) continue; // Page X of Y
                if (trimmed.matches("(?i).*confidential.*")) continue;
                if (trimmed.matches("(?i).*statement generated on.*")) continue;
                if (trimmed.matches("(?i).*this is a system generated.*")) continue;
                if (trimmed.matches("(?i).*do not reply.*")) continue;
                if (trimmed.matches("(?i).*for any queries.*")) continue;
                if (trimmed.matches("(?i).*customer service.*")) continue;
                if (trimmed.matches("(?i).*end of statement.*")) continue;
                if (trimmed.matches("(?i).*(www\\.|http).*")) continue; // websites
                if (trimmed.matches("(?i).*helpline.*")) continue;
                if (trimmed.matches("(?i).*contact us.*")) continue;
                if (trimmed.matches("(?i).*email us at.*")) continue;
                if (trimmed.matches("(?i).*branch code.*")) continue;
                if (trimmed.matches("(?i).*issued by.*")) continue;

                // keep valid lines
                sb.append(trimmed).append("\n");
            }

            // Step 4️⃣: Final cleanup
            return sb.toString().trim();
        }
    }





    /**
     * Detect if the PDF is digital (text-based) or scanned (image-only)
     */
    private boolean isDigitalPdf(byte[] pdfBytes,String password) {
        if (pdfBytes == null || pdfBytes.length == 0) return false;

        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes),password)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);

            // ✅ If extracted text length > threshold, it's a digital PDF
            if (text != null && text.trim().length() > 50) {
                log.info("🔍 Detected text content length: {}", text.trim().length());
                return true; // text-based (digital)
            } else {
                log.warn("🖼️ Very low text content detected — likely a scanned PDF.");
                return false; // scanned or image-based
            }
        } catch (Exception e) {
            log.error("⚠️ Error checking PDF type: {}", e.getMessage());
            return false;
        }
    }

    // ===========================================================
// 🔹 Check if PDF has Table Layout specifically for Transaction Section
// ===========================================================
private boolean hasTransactionTableLayout(byte[] pdfBytes,String password) {
    try (PDDocument pdfDocument = PDDocument.load(new ByteArrayInputStream(pdfBytes),password)) {

        ObjectExtractor extractor = new ObjectExtractor(pdfDocument);
        SpreadsheetExtractionAlgorithm spreadsheetExtractor = new SpreadsheetExtractionAlgorithm();

        int pagesToCheck = Math.min(pdfDocument.getNumberOfPages(), 3);
        PDFTextStripper textStripper = new PDFTextStripper();

        for (int i = 1; i <= pagesToCheck; i++) {
            Page page = extractor.extract(i);
            List<Table> tables = spreadsheetExtractor.extract(page);

            // =============================================================
            // 🔹 1️⃣ Case 1: Headings + Rows are in table cells
            // =============================================================
            if (tables != null && !tables.isEmpty()) {
                for (Table table : tables) {
                    List<List<RectangularTextContainer>> rows = table.getRows();
                    if (rows.size() < 2) continue;

                    // First row (possible header)
                    List<String> header = rows.get(0).stream()
                            .map(c -> c.getText().trim().toLowerCase())
                            .collect(Collectors.toList());

                    // Data row
                    List<String> firstDataRow = rows.get(1).stream()
                            .map(c -> c.getText().trim().toLowerCase())
                            .collect(Collectors.toList());

                    boolean headerLike = header.stream().anyMatch(h ->
                            h.contains("date") || h.contains("txn") || h.contains("description") || h.contains("debit")
                    );

                    boolean rowLike = firstDataRow.stream().anyMatch(v ->
                            v.matches("\\d{1,2}[-/ ]\\d{1,2}[-/ ]\\d{2,4}") // looks like date
                    );

                    if (headerLike || rowLike) {
                        System.out.println("📊 Table structure detected on page " + i + " (header or rows).");
                        return true;
                    }
                }
            }

            // =============================================================
            // 🔹 2️⃣ Case 2: Headings plain text, but table rows exist
            // =============================================================
            String text = extractTextFromPage(pdfDocument, textStripper, i);
            if (text.matches("(?is).*txn\\s*date.*debit.*credit.*balance.*")) {
                // Check if lines look aligned (columns aligned)
                if (looksLikeTabularText(text)) {
                    System.out.println("📄 Text-based table rows detected on page " + i);
                    return true;
                }
            }
        }

        System.out.println("⚠️ No table-like structure detected.");
    } catch (Exception e) {
        System.err.println("❌ hasTransactionTableLayout failed: " + e.getMessage());
    }
    return false;
}

    private String extractTextFromPage(PDDocument doc, PDFTextStripper stripper, int page) throws IOException {
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        return stripper.getText(doc);
    }

    private boolean looksLikeTabularText(String text) {
        String[] lines = text.split("\\r?\\n");
        int count = 0;
        for (String line : lines) {
            // detect date-like start
            if (line.trim().matches("^\\d{1,2}[-/ ]\\d{1,2}[-/ ]\\d{2,4}.*")) {
                count++;
            }
        }
        // if multiple lines start like a date → looks like transaction rows
        return count >= 3;
    }


    public List<TransactionDTO> parseUniversalTransactions(String text) {
        List<TransactionDTO> transactions = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            log.warn("⚠️ PDF text is empty. No transactions to parse.");
            return transactions;
        }

        String[] lines = text.split("\\r?\\n");
        Pattern datePattern = Pattern.compile("^(\\d{1,2}[-/\\s]\\d{1,2}[-/\\s]\\d{2,4})");

        List<String> headers = null;
        StringBuilder currentBlock = new StringBuilder();

        log.info("📄 Starting universal transaction parsing... Total lines: {}", lines.length);

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            // Detect header
            String lower = line.toLowerCase();
            if (headers == null && (lower.contains("date") &&
                    (lower.contains("cheque") || lower.contains("chq.no") || lower.contains("mode") ||
                            lower.contains("particular")||lower.contains("particulars") || lower.contains("description") || lower.contains("transaction details")))) {
                headers = splitRowIntoCells(line);
                log.info("🧭 Detected header columns: {}", headers);
                continue;
            }

            // Skip obvious summary/header lines
            if (lower.matches(".*(opening balance|closing balance|account summary|page no|statement|total|grand total|cheque|mode).*")) {
                continue;
            }

            // ❌ Skip known footer/legend/summary sections
            if (lower.contains("legends for transactions")
                    || lower.contains("sincerely,")
                    || lower.contains("team icici")
                    || lower.contains("summary of account")
                    || lower.contains("category of service")
                    || lower.contains("regd address")
                    || lower.contains("page total")
                    || lower.contains("this is a system-generated")
                    || lower.contains("your details with us")
                    || lower.contains("your base branch")
                    || lower.contains("registration no")
                    || lower.contains("page ")
                    || lower.contains("total ")
                    || lower.contains("statement of transactions")
                    || lower.contains("vat/mat/nfs - cash withdrawal")
                    || lower.contains("eba - transaction on icici direct")
                    || lower.contains("vps/ips - debit card transaction")
                    || lower.contains("top - mobile recharge")) {
                continue; // skip these lines completely
            }


            Matcher matcher = datePattern.matcher(line);
            if (matcher.find()) {
                // New transaction starts
                if (currentBlock.length() > 0) {
                    log.debug("🧩 Transaction block ready for parse:\n{}", currentBlock);
                    TransactionDTO dto = parseTransactionBlockToDto(currentBlock.toString());
                    if (dto != null) {
                        log.info("✅ Parsed transaction: {}", dto);
                        transactions.add(dto);
                    }
                    currentBlock.setLength(0);
                }

                currentBlock.append(line);
            } else if (currentBlock.length() > 0) {
                // Continuation line for current transaction
                currentBlock.append(" ").append(line);
            }
        }

        // Final block
        if (currentBlock.length() > 0) {
            log.debug("🧩 Final transaction block:\n{}", currentBlock);
            TransactionDTO dto = parseTransactionBlockToDto(currentBlock.toString());
            if (dto != null) {
                log.info("✅ Parsed final transaction: {}", dto);
                transactions.add(dto);
            }
        }

        log.info("🏁 Completed parsing. Total extracted transactions: {}", transactions.size());
        return transactions;
    }


    // helper to split row into cells (tries pipe first then multiple spaces)
    private List<String> splitRowIntoCells(String line) {
        String[] parts;
        if (line.contains("|")) {
            parts = line.split("\\|");
        } else {
            parts = line.split("\\s{2,}"); // two-or-more spaces as separator
        }
        return Arrays.stream(parts)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }

    // escape marker content to avoid accidental '||' occurrences
    private String escapeMarker(String s) {
        return s.replace("||", "|");
    }
    private String unescapeMarker(String s) {
        return s;
    }
    private static double lastBalanceValue = -1; // place this as a class-level static variable


    /**
     * parseTransactionBlockToDto now accepts chequeNo (may be null).
     * It will remove the chequeNo from description if provided, while preserving IMPS/UPI IDs.
     */
    private TransactionDTO parseTransactionBlockToDto(String block) {
        if (block == null || block.trim().isEmpty()) return null;

        TransactionDTO dto = new TransactionDTO();

        // 1️⃣ Extract and remove first date (main transaction date)
        Matcher dateMatcher = Pattern.compile("(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4})").matcher(block);
        if (dateMatcher.find()) {
            String firstDate = dateMatcher.group(1);
            dto.setTransactionDate(firstDate);
            block = block.replaceFirst(Pattern.quote(firstDate), " ");
        }

        // 2️⃣ Remove cheque-related or header-like words
        block = block.replaceAll("(?i)\\b(CHEQUE|CHQ|INSTRUMENT|NO\\.|NUMBER | MOBILE BANKING)\\b", " ");
        block = block.replaceAll("\\b\\d{1,2}:\\d{2}:\\d{2}\\b", " "); // remove time like 13:45:21
        block = block.replaceAll("(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4})", " "); // remove remaining dates

        // 3️⃣ Extract all valid monetary amounts (₹-like patterns)
        Pattern amountPattern = Pattern.compile("(\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d{1,2})?)");
        Matcher amtMatcher = amountPattern.matcher(block);
        List<String> amounts = new ArrayList<>();
        while (amtMatcher.find()) {
            String amt = amtMatcher.group(1).trim();
            String digitsOnly = amt.replaceAll("[^0-9]", "");
            if (amt.contains(",") || amt.contains(".") | digitsOnly.length() >= 4)
                amounts.add(amt);
        }

        String debit = "-", credit = "-", balance = "-";

        // ✅ NEW LOGIC (ADDED ONLY) — Smart comparison-based adjustment
        try {
            if (!amounts.isEmpty()) {
                // Get the current balance (last value)
                balance = amounts.get(amounts.size() - 1);
                double currentBalance = Double.parseDouble(balance.replaceAll(",", ""));

                // Transaction amount (previous number, if any)
                double txnAmount = 0;
                if (amounts.size() >= 2) {
                    String amt = amounts.get(amounts.size() - 2);
                    txnAmount = Double.parseDouble(amt.replaceAll(",", ""));
                }

                // Compare with previous balance (if available)
                if (lastBalanceValue != -1) {
                    if (currentBalance > lastBalanceValue) {
                        // ✅ Balance increased → Credit
                        credit = String.format("%,.2f", txnAmount);
                        debit = "-";
                        dto.setVoucherType("Receipt");
                    } else if (currentBalance < lastBalanceValue) {
                        // ✅ Balance decreased → Debit
                        debit = String.format("%,.2f", txnAmount);
                        credit = "-";
                        dto.setVoucherType("Payment");
                    }
                }

                // Update stored last balance
                lastBalanceValue = currentBalance;
            }
        } catch (Exception e) {
            log.warn("⚠️ Smart credit/debit comparison failed: {}", e.getMessage());
        }

        // Apply updated values
        dto.setDebit(debit);
        dto.setCredit(credit);
        dto.setBalance(balance);

        // 🧹 STEP: Clean up extra numeric columns at end of line (debit/credit/balance)
// Removes up to last 3 numbers like 1,00,000.00 or -7,445.00 at the end
        String cleanedBlock = block.replaceAll(
                "(\\s*|-?\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d{1,2})?){1,3}\\s*(Cr|Dr)?\\s*$",
                "").trim();

// 🧹 Also remove any lingering multiple spaces
        cleanedBlock = cleanedBlock.replaceAll("\\s+", " ").trim();

// ✅ Log before & after cleaning (for debugging)
        log.info("🧾 Before clean: {}", block);
        log.info("✅ After clean: {}", cleanedBlock);

// 🧾 Set final description
        dto.setDescription(cleanedBlock.isEmpty() ? "-" : cleanedBlock);
        return dto;
    }





}
