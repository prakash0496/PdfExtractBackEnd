package com.ExcelImport.PdfToExcel.service.MainExtractService;

import com.ExcelImport.PdfToExcel.dto.ICICIBankTransactionDTO;
import com.ExcelImport.PdfToExcel.dto.Response.TransactionDTO;
import lombok.extern.log4j.Log4j2;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Log4j2
@Service
public class TextBasedExtractorService {

    //ICICI Saving Account Extraction

    private List<TransactionDTO> parseUniversalTransactions(String text) {
        List<TransactionDTO> transactions = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            log.warn("⚠️ PDF text is empty. No transactions to parse.");
            return transactions;
        }

        String[] lines = text.split("\\r?\\n");
        Pattern datePattern = Pattern.compile("^(\\d{1,2}[-/\\s]\\d{1,2}[-/\\s]\\d{2,4})");

        List<String> headers = null;
        StringBuilder currentBlock = new StringBuilder();

        log.info("📄 Starting ICICI transaction parsing... Total lines: {}", lines.length);

        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            String lower = line.toLowerCase();

            // 🧭 Detect header
            if (headers == null && (lower.contains("date") &&
                    (lower.contains("withdrawals") ||
                            lower.contains("particular") || lower.contains("particulars") || lower.contains("description")))) {
                headers = splitRowIntoCells(line);
                log.info("🧭 Detected header columns: {}", headers);
                continue;
            }

            // 🚫 Skip summaries / footers / legends early
            if (lower.matches(".*(opening balance|closing balance|account summary|page no|statement|total|grand total|cheque|mode).*")) {
                continue;
            }

            // 🚫 Skip known non-transaction lines
            if (lower.contains("legends for transactions")
                    || lower.contains("sincerely")
                    || lower.contains("team icici")
                    || lower.contains("summary of account")
                    || lower.contains("category of service")
                    || lower.contains("regd address")
                    || lower.contains("page total")
                    || lower.contains("this is a system-generated")
                    || lower.contains("your details with us")
                    || lower.contains("your base branch")
                    || lower.contains("registration no")
                    || lower.contains("statement of transactions")
                    || lower.contains("vat/mat/nfs - cash withdrawal")
                    || lower.contains("eba - transaction on icici direct")
                    || lower.contains("vps/ips - debit card transaction")
                    || lower.contains("top - mobile recharge")) {
                continue;
            }

            // 🧠 Detect start of a new transaction (by date)
            Matcher matcher = datePattern.matcher(line);
            if (matcher.find()) {
                // flush previous block
                if (currentBlock.length() > 0) {
                    TransactionDTO dto = parseTransactionBlockToDto(currentBlock.toString());
                    if (dto != null) {
                        log.info("✅ Parsed transaction: {}", dto);
                        transactions.add(dto);
                    }
                    currentBlock.setLength(0);
                }
                currentBlock.append(line);
            } else if (currentBlock.length() > 0) {
                // 🟨 Continuation (multi-line description)
                currentBlock.append(" ").append(line);

                // 🧠 Detect footer / end-of-statement text to stop parsing
                if (lower.contains("account related other information")
                        || lower.contains("sincerely")
                        || lower.contains("team icici")
                        || lower.contains("legends for transactions")
                        || lower.contains("category of service")
                        || lower.contains("corporate office")
                        || lower.contains("registered office")
                        || lower.contains("this is an authenticated")
                        || lower.contains("www.icicibank.com")) {

                    String finalText = currentBlock.toString().trim();

                    // 🧹 Clean the unwanted numeric and footer text
                    finalText = finalText
                            // Remove trailing numeric values (debit/credit/balance)
                            .replaceAll("(\\s*-?\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?){1,3}\\s*(Cr|Dr)?\\s*$", "")
                            // Remove ICICI footer text
                            .replaceAll("(?i)(account related other information.*)$", "")
                            .replaceAll("(?i)(legends for transactions.*)$", "")
                            .replaceAll("(?i)(sincerely.*)$", "")
                            .replaceAll("(?i)(team icici.*)$", "")
                            .replaceAll("(?i)(category of service.*)$", "")
                            .replaceAll("(?i)(corporate office.*)$", "")
                            .replaceAll("(?i)(registered office.*)$", "")
                            .replaceAll("(?i)(www\\.icicibank\\.com.*)$", "")
                            .trim();
                    finalText = finalText.replaceAll("\\s+", " ").trim();

                    if (!finalText.isEmpty()) {
                        TransactionDTO dto = parseTransactionBlockToDto(finalText);
                        if (dto != null) {
                            log.info("✅ Parsed final transaction before footer: {}", dto);
                            transactions.add(dto);
                        }
                    }
                    currentBlock.setLength(0);
                    break; // 🚫 Stop reading further lines after footer
                }
            }
        }

        // 🏁 Handle last transaction block (EOF)
        if (currentBlock.length() > 0) {
            String currentText = currentBlock.toString().trim();

            // 🧹 Clean up footer text if it got appended at EOF
            currentText = currentText
                    .replaceAll("(\\s*-?\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,2})?){1,3}\\s*(Cr|Dr)?\\s*$", "")
                    .replaceAll("(?i)(account related other information.*)$", "")
                    .replaceAll("(?i)(legends for transactions.*)$", "")
                    .replaceAll("(?i)(sincerely.*)$", "")
                    .replaceAll("(?i)(team icici.*)$", "")
                    .replaceAll("(?i)(category of service.*)$", "")
                    .replaceAll("(?i)(corporate office.*)$", "")
                    .replaceAll("(?i)(registered office.*)$", "")
                    .replaceAll("(?i)(www\\.icicibank\\.com.*)$", "")
                    .trim();
            currentText = currentText.replaceAll("\\s+", " ").trim();

            if (!currentText.isEmpty()) {
                TransactionDTO dto = parseTransactionBlockToDto(currentText);
                if (dto != null) {
                    log.info("✅ Parsed final transaction (EOF): {}", dto);
                    transactions.add(dto);
                }
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
            parts = line.split("\\s{1,}"); // two-or-more spaces as separator
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

    /**
     * parseTransactionBlockToDto now accepts chequeNo (may be null).
     * It will remove the chequeNo from description if provided, while preserving IMPS/UPI IDs.
     */
    private static double lastBalanceValue = -1; // place this as a class-level static variable

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

        // 3️⃣ Extract all valid monetary amounts
        Pattern amountPattern = Pattern.compile("(\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d{1,2})?)");
        Matcher amtMatcher = amountPattern.matcher(block);
        List<String> amounts = new ArrayList<>();
        while (amtMatcher.find()) {
            String amt = amtMatcher.group(1).trim();
            String digitsOnly = amt.replaceAll("[^0-9]", "");
            if (amt.contains(",") || amt.contains(".") || digitsOnly.length() >= 4)
                amounts.add(amt);
        }


        // 4️⃣ Smart logic for debit/credit/balance — compatible with both ICICI formats
        String debit = "-", credit = "-", balance = "-";
        try {
            if (!amounts.isEmpty()) {
                // 🧠 Detect "structured" format (like icici 4.pdf) → three numeric columns in a row
                if (amounts.size() >= 3 && block.matches("(?i).*\\b withdrawals|deposit|autosweep|reverse sweep/.*")) {
                    // Usually: Withdrawals, Deposits, Balance
                    String possibleDebit = amounts.get(amounts.size() - 3);
                    String possibleCredit = amounts.get(amounts.size() - 2);
                    String possibleBalance = amounts.get(amounts.size() - 1);

                    double debitVal = Double.parseDouble(possibleDebit.replaceAll(",", ""));
                    double creditVal = Double.parseDouble(possibleCredit.replaceAll(",", ""));
                    double balanceVal = Double.parseDouble(possibleBalance.replaceAll(",", ""));

                    if (debitVal > 0) {
                        debit = String.format("%,.2f", debitVal);
                        credit = "0.00";
                        dto.setVoucherType("Payment");
                    } else if (creditVal > 0) {
                        credit = String.format("%,.2f", creditVal);
                        debit = "0.00";
                        dto.setVoucherType("Receipt");
                    }
                    balance = String.format("%,.2f", Math.abs(balanceVal));
                    lastBalanceValue = balanceVal;
                } else {
                    // 🔹 Fallback to old savings logic
                    balance = amounts.get(amounts.size() - 1);
                    double currentBalance = Double.parseDouble(balance.replaceAll(",", ""));
                    double txnAmount = 0;
                    if (amounts.size() >= 2) {
                        String amt = amounts.get(amounts.size() - 2);
                        txnAmount = Double.parseDouble(amt.replaceAll(",", ""));
                    }

                    if (lastBalanceValue != -1) {
                        if (currentBalance > lastBalanceValue) {
                            credit = String.format("%,.2f", txnAmount);
                            debit = "-";
                            dto.setVoucherType("Receipt");
                        } else if (currentBalance < lastBalanceValue) {
                            debit = String.format("%,.2f", txnAmount);
                            credit = "-";
                            dto.setVoucherType("Payment");
                        }
                    }
                    lastBalanceValue = currentBalance;
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Smart credit/debit comparison failed: {}", e.getMessage());
        }

// Apply updated values
        dto.setDebit(debit);
        dto.setCredit(credit);
        dto.setBalance(balance);

        // 🧹 STEP: Clean up extra numeric columns at end of line
        String cleanedBlock = block.replaceAll(
                "(\\s*|-?\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d{1,2})?){1,3}\\s*(Cr|Dr)?\\s*$",
                ""
        ).trim();

        // 🧹 Also remove any lingering multiple spaces
        cleanedBlock = cleanedBlock.replaceAll("\\s+", " ").trim();

        // ✅ Log before & after cleaning (for debugging)
        log.info("🧾 Before clean: {}", block);
        log.info("✅ After clean: {}", cleanedBlock);

        // 🧾 Set final description
        dto.setDescription(cleanedBlock.isEmpty() ? "-" : cleanedBlock);
        return dto;
    }



    //  Canara Bank Transactions
    //  ========================

    public List<TransactionDTO> parseCanaraBankTransactions(String text) {
        List<TransactionDTO> transactions = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return transactions;

        String[] lines = text.split("\\r?\\n");
        Pattern datePattern = Pattern.compile("^(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4})");

        StringBuilder currentBlock = new StringBuilder();
        boolean inTransactionSection = false;

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            String lower = line.toLowerCase();

            // ignore header/footer sections
            if (lower.contains("opening balance") || lower.contains("closing balance") ||
                    lower.contains("disclaimer") || lower.contains("ombudsman") ||
                    lower.contains("statement for a/c") || lower.contains("page") ||
                    lower.contains("end of statement")) continue;

            Matcher matcher = datePattern.matcher(line);
            if (matcher.find()) {
                inTransactionSection = true;

                // 🆕 If a previous transaction block exists, parse it
                if (currentBlock.length() > 0) {
                    TransactionDTO dto = parseCanaraBlock(currentBlock.toString());
                    if (dto != null) transactions.add(dto);
                    currentBlock.setLength(0);
                }

                // Start new block with date
                currentBlock.append(line);
            } else if (inTransactionSection) {
                // Append continuation lines
                currentBlock.append(" ").append(line);
            }
        }

        // Parse last transaction block
        if (currentBlock.length() > 0) {
            TransactionDTO dto = parseCanaraBlock(currentBlock.toString());
            if (dto != null) transactions.add(dto);
        }

        return transactions;
    }

    /**
     * Parse one Canara-style transaction block
     */
    private TransactionDTO parseCanaraBlock(String block) {
        TransactionDTO dto = new TransactionDTO();

        // 1️⃣ Extract transaction date
        Matcher dateMatcher = Pattern.compile("(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4})").matcher(block);
        if (dateMatcher.find()) dto.setTransactionDate(dateMatcher.group(1));

        // 2️⃣ Extract cheque number
        Matcher chqMatcher = Pattern.compile("(?i)Chq[:\\s]*(\\d+)").matcher(block);
        String chequeNo = chqMatcher.find() ? chqMatcher.group(1) : null;

        // 3️⃣ Extract all numeric values (Deposits, Withdrawals, Balance)
        Pattern amountPattern = Pattern.compile("(\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d{1,2})?)");
        Matcher amtMatcher = amountPattern.matcher(block);
        List<String> amounts = new ArrayList<>();
        while (amtMatcher.find()) {
            String amt = amtMatcher.group(1);
            if (amt.matches(".*\\d.*")) amounts.add(amt);
        }

        String deposit = "-", withdrawal = "-", balance = "-";
        if (amounts.size() == 3) {
            deposit = amounts.get(0);
            withdrawal = amounts.get(1);
            balance = amounts.get(2);
        } else if (amounts.size() == 2) {
            withdrawal = amounts.get(0);
            balance = amounts.get(1);
        } else if (amounts.size() == 1) {
            balance = amounts.get(0);
        }

        dto.setCredit(deposit);
        dto.setDebit(withdrawal);
        dto.setBalance(balance);

        // 4️⃣ Clean and build description
        String cleaned = block
                .replaceAll("(?i)Chq[:\\s]*\\d*", "")
                .replaceAll("(\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d{1,2})?)", "")
                .replaceAll("\\s+", " ")
                .trim();

        // Remove date repetition
        cleaned = cleaned.replaceAll("(\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4})", "").trim();

        dto.setDescription(cleaned.isEmpty() ? "-" : cleaned);

        // 5️⃣ Determine voucher type
        if (!deposit.equals("-") && !deposit.equals("0.00")) dto.setVoucherType("Receipt");
        else if (!withdrawal.equals("-") && !withdrawal.equals("0.00")) dto.setVoucherType("Payment");
        else dto.setVoucherType("-");

        return dto;
    }


    // Indian Bank Transaction
    // =======================

//    // ✅ Read PDF text
//    public String extractTextFromPDF() throws IOException {
//        try (PDDocument document = PDDocument.load(file)) {
//            PDFTextStripper stripper = new PDFTextStripper();
//            return stripper.getText(document);
//        }
//    }

    // ✅ Extract transactions
    public List<TransactionDTO> extractTransactions(String text) {
        List<TransactionDTO> transactions = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) return transactions;

        // Split by lines
        String[] lines = text.split("\\r?\\n");
        StringBuilder current = new StringBuilder();
        Pattern datePattern = Pattern.compile("^(\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{4})");

        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty()) continue;

            String lower = line.toLowerCase();
            if (lower.matches(".*(account activity|opening balance|closing balance|account summary|total|page|thank you|statement).*"))
                continue;

            Matcher matcher = datePattern.matcher(line);
            if (matcher.find()) {
                if (current.length() > 0) {
                    TransactionDTO dto = parseBlockToDto(current.toString().trim());
                    if (dto != null) transactions.add(dto);
                    current.setLength(0);
                }
                current.append(line);
            } else if (current.length() > 0) {
                current.append(" ").append(line);
            }
        }

        if (current.length() > 0) {
            TransactionDTO dto = parseBlockToDto(current.toString().trim());
            if (dto != null) transactions.add(dto);
        }

        return transactions;
    }

    private TransactionDTO parseBlockToDto(String block) {
        TransactionDTO dto = new TransactionDTO();

        // Remove footer noise like "Ending Balance" or "Indian Bank"
        block = block.replaceAll("(?i)ending balance.*", "")
                .replaceAll("(?i)indian bank.*", "")
                .trim();

        // 1️⃣ Extract Date
        Matcher dateMatcher = Pattern.compile("(\\d{1,2}\\s+[A-Za-z]{3,9}\\s+\\d{4})").matcher(block);
        if (dateMatcher.find()) {
            dto.setTransactionDate(dateMatcher.group(1));
            block = block.replaceFirst(Pattern.quote(dateMatcher.group(1)), "").trim();
        }

        // 2️⃣ Extract all INR values
        Matcher amtMatcher = Pattern.compile("(-?\\s*INR\\s*\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d{1,2})?)").matcher(block);
        List<String> amounts = new ArrayList<>();
        while (amtMatcher.find()) amounts.add(amtMatcher.group(1).trim());

        String debit = "0.00", credit = "0.00", balance = "0.00";

        if (amounts.size() == 2) {
            String first = amounts.get(0);
            String second = amounts.get(1);

            // 🧠 Case 1: "INR 32.00  - INR 1,190.65" → Debit
            if (block.matches(".*INR\\s*\\d.*-\\s*INR\\s*\\d.*")) {
                debit = cleanAmount(first);
                balance = cleanAmount(second);
            }
            // 🧠 Case 2: "- INR 24.00 INR 1,214.65" → Credit
            else if (block.matches(".*-\\s*INR\\s*\\d.*INR\\s*\\d.*")) {
                credit = cleanAmount(first);
                balance = cleanAmount(second);
            }
            // 🧠 Case 3: “CREDIT INTEREST” keyword → Credit
            else if (block.toUpperCase().contains("CREDIT")) {
                credit = cleanAmount(first);
                balance = cleanAmount(second);
            }
            // 🧠 Fallback — assume Debit
            else {
                debit = cleanAmount(first);
                balance = cleanAmount(second);
            }
        } else if (amounts.size() == 3) {
            // Format: Debit, Credit, Balance
            debit = cleanAmount(amounts.get(0));
            credit = cleanAmount(amounts.get(1));
            balance = cleanAmount(amounts.get(2));
        } else if (amounts.size() == 1) {
            balance = cleanAmount(amounts.get(0));
        }

        dto.setDebit(debit);
        dto.setCredit(credit);
        dto.setBalance(balance);

        // 3️⃣ Clean up description
        String desc = block.replaceAll("(-?\\s*INR\\s*\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d{1,2})?)+", "")
                .replaceAll("\\s{2,}", " ")
                .trim();
        dto.setDescription(desc);

        // 4️⃣ Voucher type
        if (!credit.equals("0.00")) dto.setVoucherType("Receipt");
        else if (!debit.equals("0.00")) dto.setVoucherType("Payment");
        else dto.setVoucherType("-");

        return dto;
    }

    private String cleanAmount(String amt) {
        if (amt == null) return "0.00";
        return amt.replaceAll("[^0-9.]", "").trim();
    }


    // ICICI Extraction

    public List<TransactionDTO> extractICICI(byte[] pdfBytes) throws Exception {
        List<TransactionDTO> transactions = new ArrayList<>();

        try (PDDocument pdfDocument = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            PDFRenderer renderer = new PDFRenderer(pdfDocument);
            PDFTextStripper textStripper = new PDFTextStripper();

            int pageCount = pdfDocument.getNumberOfPages();
            for (int i = 0; i < pageCount; i++) {
                textStripper.setStartPage(i + 1);
                textStripper.setEndPage(i + 1);

                String pageText = textStripper.getText(pdfDocument).trim();

                if (pageText.length() > 80) {
                    List<TransactionDTO> textTx = parseUniversalTransactions(pageText);
                    transactions.addAll(textTx);
                } else {
                    BufferedImage image = renderer.renderImageWithDPI(i, 600);
                    ITesseract tesseract = new Tesseract();
                    tesseract.setDatapath("E:/PdfExtract/PdfToExcel/PdfToExcel/src/main/java/com/ExcelImport/PdfToExcel/tessdata"); // update to your path
                    String ocrText = tesseract.doOCR(image);
                    List<TransactionDTO> ocrTx = parseUniversalTransactions(ocrText);
                    transactions.addAll(ocrTx);
                }
            }
        }
        return transactions;
    }

    //ICICI Current Account Extraction

    public List<TransactionDTO> extractUsingTabula(byte[] pdfBytes) {
        List<TransactionDTO> transactions = new ArrayList<>();

        Pattern datePattern = Pattern.compile("^\\d{2}-\\d{2}-\\d{4}$");
        Pattern maybeNumeric = Pattern.compile(".*[0-9].*");
        Pattern balancePattern = Pattern.compile("^-?\\s*[0-9,]+(?:\\.\\d{1,2})?\\s*(Cr|DR|Dr|cr|dr)?$");

        try (PDDocument pdfDocument = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            ObjectExtractor extractor = new ObjectExtractor(pdfDocument);
            SpreadsheetExtractionAlgorithm sea = new SpreadsheetExtractionAlgorithm();

            for (int page = 1; page <= pdfDocument.getNumberOfPages(); page++) {
                Page pdfPage = extractor.extract(page);
                List<Table> tables = sea.extract(pdfPage);

                for (Table table : tables) {
                    for (List<RectangularTextContainer> row : table.getRows()) {

                        List<String> cells = row.stream()
                                .map(cell -> cell.getText().trim())
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.toList());

                        if (cells.isEmpty()) continue;

                        String first = cells.get(0);
                        if (!datePattern.matcher(first).find()) continue;

                        // find balance
                        int balanceIdx = -1;
                        for (int i = cells.size() - 1; i >= 0; i--) {
                            String c = cells.get(i).replaceAll("\\s+", "");
                            if (balancePattern.matcher(c).matches()) {
                                balanceIdx = i;
                                break;
                            }
                        }
                        if (balanceIdx == -1) balanceIdx = cells.size() - 1;
                        String balance = cells.get(balanceIdx);

                        // ✅ Detect cheque number (3–6 digits, short numeric cell)
                        String chequeNo = null;
                        int chequeIdx = -1;
                        for (int i = 1; i < balanceIdx; i++) {
                            String val = cells.get(i).replaceAll(",", "").trim();
                            if (val.matches("^\\d{3,6}$")) {
                                chequeNo = val;
                                chequeIdx = i;
                                break;
                            }
                        }

                        // ✅ numeric-like cells (amount columns), skip cheque number column
                        List<Integer> numericIdxs = new ArrayList<>();
                        for (int i = 2; i < balanceIdx; i++) {
                            if (i == chequeIdx) continue; // skip cheque no
                            if (maybeNumeric.matcher(cells.get(i)).find()) {
                                numericIdxs.add(i);
                            }
                        }

                        List<String> amountCells = numericIdxs.stream()
                                .map(cells::get)
                                .collect(Collectors.toList());

                        double withdrawals = 0.0, deposits = 0.0, autosweep = 0.0, reverseSweep = 0.0;

                        // helper for numeric parsing
                        java.util.function.Function<String, Double> toNum = (s) -> {
                            if (s == null) return 0.0;
                            String cleaned = s.replaceAll("[^0-9.\\-]", "");
                            if (cleaned.isBlank()) return 0.0;
                            try {
                                return Double.parseDouble(cleaned);
                            } catch (Exception ex) {
                                return 0.0;
                            }
                        };

                        // align from right: reverseSweep, autosweep, deposits, withdrawals
                        for (int i = 0; i < amountCells.size(); i++) {
                            int fromRightIndex = amountCells.size() - 1 - i;
                            String val = amountCells.get(fromRightIndex);
                            double num = toNum.apply(val);

                            if (i == 0) reverseSweep = num;
                            else if (i == 1) autosweep = num;
                            else if (i == 2) deposits = num;
                            else if (i == 3) withdrawals = num;
                        }

                        // ✅ description — everything between date and first numeric or cheque number
                        int descEndIndex = 1;
                        if (!numericIdxs.isEmpty()) {
                            descEndIndex = Math.min(numericIdxs.get(0), balanceIdx);
                        } else if (chequeIdx > 0) {
                            descEndIndex = Math.min(chequeIdx, balanceIdx);
                        }

                        StringBuilder desc = new StringBuilder();
                        for (int i = 1; i < descEndIndex; i++) {
                            if (i < cells.size()) {
                                if (desc.length() > 0) desc.append(" ");
                                desc.append(cells.get(i));
                            }
                        }
                        String description = desc.length() > 0 ? desc.toString().trim() : (cells.size() > 1 ? cells.get(1) : "-");

                        // combine autosweep into debit, reverseSweep into credit
                        double debit = withdrawals + autosweep;
                        double credit = deposits + reverseSweep;

                        TransactionDTO tx = new TransactionDTO();
                        tx.setTransactionDate(first);
                        tx.setDescription(description);
                        tx.setDebit(debit > 0 ? String.format("%.2f", debit) : "-");
                        tx.setCredit(credit > 0 ? String.format("%.2f", credit) : "-");
                        tx.setBalance(balance);

                        if (debit > 0)
                            tx.setVoucherType("Payment");
                        else if (credit > 0)
                            tx.setVoucherType("Receipt");
                        else
                            tx.setVoucherType("-");

                        transactions.add(tx);
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("⚠️ Tabula extraction error: " + e.getMessage());
        }

        return transactions;
    }

}


