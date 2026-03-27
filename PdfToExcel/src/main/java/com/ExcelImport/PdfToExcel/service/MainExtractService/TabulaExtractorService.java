package com.ExcelImport.PdfToExcel.service.MainExtractService;


import com.ExcelImport.PdfToExcel.dto.Response.TransactionDTO;
import lombok.extern.log4j.Log4j2;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.extractors.BasicExtractionAlgorithm;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

import java.io.ByteArrayInputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Log4j2
@Service
public class TabulaExtractorService {


    public List<List<String>> extractTableFromPdf(byte[] pdfBytes, String password) throws Exception {

        List<List<String>> tableData = new ArrayList<>();

        try (PDDocument document = PDDocument.load(
                new ByteArrayInputStream(pdfBytes), password)) {

            ObjectExtractor extractor = new ObjectExtractor(document);
            SpreadsheetExtractionAlgorithm spreadsheet = new SpreadsheetExtractionAlgorithm();

            int totalPages = document.getNumberOfPages();

            for (int pageIndex = 1; pageIndex <= totalPages; pageIndex++) {

                Page page = extractor.extract(pageIndex);
                List<Table> tables = spreadsheet.extract(page);

                if (tables == null || tables.isEmpty()) continue;

                for (Table table : tables) {

                    for (List<RectangularTextContainer> row : table.getRows()) {

                        List<String> rowData = new ArrayList<>(row.size());
                        boolean validRow = false;

                        for (RectangularTextContainer cell : row) {

                            String text = cell.getText();

                            if (text != null) {
                                text = text.trim().replaceAll("\\s+", " ");
                                if (!text.isEmpty()) validRow = true;
                                rowData.add(text);
                            } else {
                                rowData.add("");
                            }
                        }

                        // Skip empty rows
                        if (!validRow) continue;

                        // Skip header duplicates (common in bank statements)
                        if (isHeaderRow(rowData)) continue;

                        tableData.add(rowData);
                    }
                }
            }
        }

        return tableData;
    }

    private boolean isHeaderRow(List<String> row) {
        String combined = String.join(" ", row).toLowerCase();
        return combined.contains("date") &&
                combined.contains("balance");
    }

    //Canara Bank Extraction

    public List<TransactionDTO> CanaraBankMapDto(List<List<String>> tableRows) {
        List<TransactionDTO> transactions = new ArrayList<>();

        for (List<String> row : tableRows) {

            // Skip header or invalid rows
            if (row.size() < 8 || row.get(0).toLowerCase().contains("txn") || row.get(0).toLowerCase().contains("date") || row.get(0).toLowerCase().contains("opening") || row.get(2).toLowerCase().contains("b/f") || row.get(3).toLowerCase().contains("b/f") || row.get(4).toLowerCase().contains("b/f") || row.get(7).toLowerCase().contains("0.0"))
                continue;

            // Clean cells
            for (int i = 0; i < row.size(); i++) {
                row.set(i, row.get(i).replaceAll("[\\r\\n]", "").trim());
            }

            TransactionDTO tx = new TransactionDTO();

            // 🗓 Transaction and Value Dates
            tx.setTransactionDate(row.get(0));


            if (row.size() > 3) {

                String desc = "-";

                for (int i = 3; i <= 4 && i < row.size(); i++) {

                    if (row.get(i) == null) continue;

                    String value = row.get(i).trim();

                    if (value.isEmpty()) continue;

                    // Ignore header
                    if (value.equalsIgnoreCase("description")) continue;

                    // Ignore pure numbers (amount columns)
                    if (value.matches("-?\\d+(,\\d+)*(\\.\\d+)?")) continue;

                    // First valid text → treat as description
                    desc = value;
                    break;
                }

                tx.setDescription(desc);
            }
            //      tx.setDescription(description);

            // 💰 Debit / Credit / Balance
            tx.setDebit(row.size() > 5 && !row.get(5).isEmpty() ? cleanAmount(row.get(5)) : "0.0");
            tx.setCredit(row.size() > 6 && !row.get(6).isEmpty() ? cleanAmount(row.get(6)) : "0.0");
            tx.setBalance(row.size() > 7 && !row.get(7).isEmpty() ? cleanAmount(row.get(7)) : "0.0");

            // 🧾 Voucher Type Logic
            if (tx.getCredit() != null && !tx.getCredit().equals("0.0") && !tx.getCredit().isEmpty()) {
                tx.setVoucherType("Receipt");
            } else if (tx.getDebit() != null && !tx.getDebit().equals("0.0") && !tx.getDebit().isEmpty()) {
                tx.setVoucherType("Payment");
            } else {
                tx.setVoucherType("-");
            }

            transactions.add(tx);
        }

        return transactions;
    }

    //IDBI Bank Extraction

    public List<TransactionDTO> IdbiBankMapDto(List<List<String>> tableRows) {

        List<TransactionDTO> transactions = new ArrayList<>();

        if (tableRows == null || tableRows.isEmpty()) {
            return transactions;
        }

        // 🔍 STEP 1: Detect unwanted column index from header
        List<String> headerRow = tableRows.get(0);
        List<Integer> removeIndexes = new ArrayList<>();

        for (int i = 0; i < headerRow.size(); i++) {
            String header = headerRow.get(i).toLowerCase().trim();

            if (header.contains("cheque no") || header.contains("ref no")) {
                removeIndexes.add(i);
            }
        }

        // 🔥 Remove from last index to first (very important)
        Collections.sort(removeIndexes, Collections.reverseOrder());

        for (List<String> row : tableRows) {
            for (int index : removeIndexes) {
                if (row.size() > index) {
                    row.remove(index);
                }
            }
        }

        // 🔄 STEP 2: Process Cleaned Rows
        for (List<String> row : tableRows) {
            if (row == null || row.size() < 5) {
                continue;
            }
            String joined = String.join(" ", row).toLowerCase();
            // Skip header row
            if (joined.contains("txn date") || joined.contains("description")) {
                continue;
            }
            // 🧹 Clean cells
            for (int i = 0; i < row.size(); i++) {
                row.set(i, row.get(i).replaceAll("[\\r\\n]", "").trim());
            }
            TransactionDTO tx = new TransactionDTO();
            // 🗓 Transaction Date
            tx.setTransactionDate(row.size() > 1 ? row.get(1) : "-");
            // 🧾 Description
            tx.setDescription(row.size() > 3 && !row.get(3).isEmpty() ? row.get(3) : "-");

            if (row.size() > 5) {
                String col5 = row.get(5).toLowerCase().trim();

                if (col5.contains("dr") || col5.contains("cr")) {
                    if (col5.contains("dr")) {
                        tx.setDebit(row.size() > 7 && !row.get(7).isEmpty() ? cleanAmount(row.get(7)) : "-");
                        tx.setCredit("-");
                        tx.setBalance(row.size() > 8 && !row.get(8).isEmpty() ? cleanAmount(row.get(8)) : "-");
                    } else {
                        tx.setCredit(row.size() > 7 && !row.get(7).isEmpty() ? cleanAmount(row.get(7)) : "-");
                        tx.setDebit("-");
                        tx.setBalance(row.size() > 8 && !row.get(8).isEmpty() ? cleanAmount(row.get(8)) : "-");
                    }
                } else {
                    tx.setDebit(row.size() > 5 && !row.get(5).isEmpty() ? cleanAmount(row.get(5)) : "-");
                    tx.setCredit(row.size() > 6 && !row.get(6).isEmpty() ? cleanAmount(row.get(6)) : "-");
                    tx.setBalance(row.size() > 7 && !row.get(7).isEmpty() ? cleanAmount(row.get(7)) : "-");
                }
            }

            // 🧾 Voucher Type
            if (!tx.getCredit().equals("-") && !tx.getCredit().isEmpty()) {
                tx.setVoucherType("Receipt");
            } else if (!tx.getDebit().equals("-") && !tx.getDebit().isEmpty()) {
                tx.setVoucherType("Payment");
            } else {
                tx.setVoucherType("-");
            }

            transactions.add(tx);
        }

        return transactions;
    }


    // StateBank Extraction

    public List<TransactionDTO> statebankMapDto(List<List<String>> tableRows) {
        List<TransactionDTO> transactions = new ArrayList<>();

        for (List<String> row : tableRows) {
            if (row == null || row.isEmpty()) continue;

            // 🧹 Clean each cell
            for (int i = 0; i < row.size(); i++) {
                String cell = row.get(i);
                row.set(i, (cell == null ? "" : cell.replaceAll("[\\r\\n]", "").trim()));
            }

            // 🧾 Skip fully empty rows (all blank or "-")
            boolean allEmpty = row.stream()
                    .allMatch(cell -> cell == null || cell.trim().isEmpty() || cell.trim().equals("-"));
            if (allEmpty) continue;

            // 🧾 Skip header or invalid rows
            if (row.get(0).toLowerCase().contains("txn") || row.get(0).toLowerCase().contains("date")) continue;

            TransactionDTO dto = new TransactionDTO();

            boolean hasBranchCode = row.size() >= 8;

            dto.setTransactionDate(formatTallyDate(getValue(row, 0)));
            dto.setDescription(getValue(row, 2));

            if (hasBranchCode) {
                dto.setDebit(cleanAmount(getValue(row, 5)));
                dto.setCredit(cleanAmount(getValue(row, 6)));
                dto.setBalance(cleanAmount(getValue(row, 7)));
            } else {
                dto.setDebit(cleanAmount(getValue(row, 4)));
                dto.setCredit(cleanAmount(getValue(row, 5)));
                dto.setBalance(cleanAmount(getValue(row, 6)));
            }

            // 💰 Voucher type logic
            if (!dto.getCredit().equals("-")) dto.setVoucherType("Receipt");
            else if (!dto.getDebit().equals("-")) dto.setVoucherType("Payment");
            else dto.setVoucherType("-");

            // 🚫 Skip DTOs that are all "-"
            boolean allDash = Stream.of(
                    dto.getTransactionDate(),
                    dto.getDescription(),
                    dto.getDebit(),
                    dto.getCredit(),
                    dto.getBalance()
            ).allMatch(val -> val == null || val.trim().equals("-") || val.trim().isEmpty());

            if (allDash) continue;

            transactions.add(dto);
        }

        return transactions;
    }

    // Indian Bank

    public List<TransactionDTO> indianbankMapDto(List<List<String>> tableRows) {
        List<TransactionDTO> transactions = new ArrayList<>();

        for (List<String> row : tableRows) {
            if (row == null || row.isEmpty()) continue;

            // 🧹 Clean each cell
            for (int i = 0; i < row.size(); i++) {
                String cell = row.get(i);
                row.set(i, (cell == null ? "" : cell.replaceAll("[\\r\\n]", "").trim()));
            }

            // 🧾 Skip fully empty rows (all blank or "-")
            boolean allEmpty = row.stream()
                    .allMatch(cell -> cell == null || cell.trim().isEmpty() || cell.trim().equals("-"));
            if (allEmpty) continue;

            // 🧾 Skip header or invalid rows
            if (row.get(0).toLowerCase().contains("txn") || row.get(0).toLowerCase().contains("date")) continue;

            TransactionDTO dto = new TransactionDTO();

            boolean hasBranchCode = row.size() >= 8;

            dto.setTransactionDate(formatTallyDate(getValue(row, 0)));
            dto.setDescription(getValue(row, 5));

//            if (hasBranchCode) {
                dto.setDebit(cleanAmount(getValue(row, 3)));
                dto.setCredit(cleanAmount(getValue(row, 2)));
                dto.setBalance(cleanAmount(getValue(row, 4)));
//            } else {
//                dto.setDebit(cleanAmount(getValue(row, 4)));
//                dto.setCredit(cleanAmount(getValue(row, 5)));
//                dto.setBalance(cleanAmount(getValue(row, 6)));
//            }

            // 💰 Voucher type logic
            if (!dto.getCredit().equals("-")) dto.setVoucherType("Receipt");
            else if (!dto.getDebit().equals("-")) dto.setVoucherType("Payment");
            else dto.setVoucherType("-");

            // 🚫 Skip DTOs that are all "-"
            boolean allDash = Stream.of(
                    dto.getTransactionDate(),
                    dto.getDescription(),
                    dto.getDebit(),
                    dto.getCredit(),
                    dto.getBalance()
            ).allMatch(val -> val == null || val.trim().equals("-") || val.trim().isEmpty());

            if (allDash) continue;

            transactions.add(dto);
        }

        return transactions;
    }


    //Kotak

    public List<TransactionDTO> kotakbankMapDto(List<List<String>> tableRows) {

        List<TransactionDTO> transactions = new ArrayList<>();

        for (List<String> row : tableRows) {

            if (row == null || row.isEmpty()) continue;

            // 🧹 Clean each cell
            for (int i = 0; i < row.size(); i++) {
                String cell = row.get(i);
                row.set(i, (cell == null ? "" : cell.replaceAll("[\\r\\n]", "").trim()));
            }

            // 🧾 Skip fully empty rows
            boolean allEmpty = row.stream()
                    .allMatch(cell -> cell == null || cell.trim().isEmpty() || cell.trim().equals("-"));
            if (allEmpty) continue;

            // 🧾 Skip header rows
            if (row.get(0).toLowerCase().contains("txn") ||
                    row.get(0).toLowerCase().contains("date")) continue;

            // 🔴 IMPORTANT: Skip row if balance is empty
            String balanceValue = getValue(row, 6);
            if (balanceValue == null || balanceValue.trim().isEmpty() || balanceValue.trim().equals("-")) {
                continue;   // 🚫 Entire row skipped
            }

            TransactionDTO dto = new TransactionDTO();

            dto.setTransactionDate(formatTallyDate(getValue(row, 1)));
            dto.setDescription(getValue(row, 2));
            dto.setDebit(cleanAmount(getValue(row, 4)));
            dto.setCredit(cleanAmount(getValue(row, 5)));
            dto.setBalance(cleanAmount(balanceValue));

            // 💰 Voucher type logic
            if (!dto.getCredit().equals("-")) {
                dto.setVoucherType("Receipt");
            } else if (!dto.getDebit().equals("-")) {
                dto.setVoucherType("Payment");
            } else {
                dto.setVoucherType("-");
            }

            transactions.add(dto);
        }

        return transactions;
    }

    //AXIS BANK

    public List<TransactionDTO> axisbankMapDto(List<List<String>> tableRows) {

        List<TransactionDTO> transactions = new ArrayList<>();

        for (List<String> row : tableRows) {

            if (row == null || row.isEmpty()) continue;

            // 🧹 Clean each cell
            for (int i = 0; i < row.size(); i++) {
                String cell = row.get(i);
                row.set(i, (cell == null ? "" : cell.replaceAll("[\\r\\n]", "").trim()));
            }

            // 🧾 Skip fully empty rows
            boolean allEmpty = row.stream()
                    .allMatch(cell -> cell == null || cell.trim().isEmpty() || cell.trim().equals("-"));
            if (allEmpty) continue;

            // 🧾 Skip header rows
            if (row.get(0).toLowerCase().contains("txn") ||
                    row.get(0).toLowerCase().contains("date")) continue;

            // 🔴 IMPORTANT: Skip row if balance is empty
            String balanceValue = getValue(row, 5);
            if (balanceValue == null || balanceValue.trim().isEmpty() || balanceValue.trim().equals("-")) {
                continue;   // 🚫 Entire row skipped
            }

            TransactionDTO dto = new TransactionDTO();

            dto.setTransactionDate(formatTallyDate(getValue(row, 0)));
            dto.setDescription(getValue(row, 2));
            dto.setDebit(cleanAmount(getValue(row, 3)));
            dto.setCredit(cleanAmount(getValue(row, 4)));
            dto.setBalance(cleanAmount(balanceValue));

            // 💰 Voucher type logic
            if (!dto.getCredit().equals("-")) {
                dto.setVoucherType("Receipt");
            } else if (!dto.getDebit().equals("-")) {
                dto.setVoucherType("Payment");
            } else {
                dto.setVoucherType("-");
            }

            transactions.add(dto);
        }

        return transactions;
    }




// 🔹 Helper Methods

    private String getValue(List<String> row, int index) {
        return (index < row.size() && row.get(index) != null && !row.get(index).trim().isEmpty())
                ? row.get(index).trim()
                : "-";
    }

    private String cleanAmount(String value) {
        if (value == null || value.trim().isEmpty() || value.equals("-")) return "-";
        return value.replace(",", "").replaceAll("[^0-9.]", "").trim();
    }

    private String formatTallyDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty() || "-".equals(dateStr.trim())) {
            return "-";
        }

        // Normalize input (newlines, extra spaces, separators)
        dateStr = dateStr
                .replace("\n", " ")
                .replace("-", "/")
                .replaceAll("\\s+", " ")
                .trim();

        SimpleDateFormat tallyDate = new SimpleDateFormat("dd-MM-yyyy");

        SimpleDateFormat[] inputFormats = {
                new SimpleDateFormat("dd/MM/yyyy"),
                new SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
        };

        for (SimpleDateFormat inputFormat : inputFormats) {
            try {
                Date parsedDate = inputFormat.parse(dateStr);
                return tallyDate.format(parsedDate);
            } catch (ParseException ignored) {
            }
        }

        return "-"; // Graceful fallback
    }


    //City_Union Bank Extraction

    public List<TransactionDTO> cityUnionBankMapDto(List<List<String>> tableRows) {
        List<TransactionDTO> transactionDTOS = new ArrayList<>();

        boolean isFirstRow = true;

        for (List<String> row : tableRows) {

            // Skip the first row (headings)
            if (isFirstRow) {
                isFirstRow = false;
                continue;
            }

            // Skip if row is empty
            if (isRowEmpty(row)) {
                continue;
            }

            // Clean up each cell in the row
            for (int i = 0; i < row.size(); i++) {
                row.set(i, row.get(i).replaceAll("[\\r\\n]", "").trim());
            }

            // Skip if after cleaning, the row is empty or contains header keywords
//            if (isRowEmpty(row) || containsHeaderKeywords(row)) {
//                continue;
//            }

            // 🚫 Skip "TOTAL" rows
            if (isTotalRow(row)) {
                continue;
            }

            TransactionDTO tx = new TransactionDTO();

            tx.setTransactionDate(row.get(0));
            tx.setDescription(row.size() > 1 && !row.get(1).isEmpty() ? row.get(1) : "-");
            tx.setDebit(row.size() > 3 && !row.get(3).isEmpty() ? cleanAmount(row.get(3)) : "-");
            tx.setCredit(row.size() > 4 && !row.get(4).isEmpty() ? cleanAmount(row.get(4)) : "-");
            tx.setBalance(row.size() > 5 && !row.get(5).isEmpty() ? cleanAmount(row.get(5)) : "-");

            // 🧾 Voucher Type Logic
            if (tx.getCredit() != null && !tx.getCredit().equals("-") && !tx.getCredit().isEmpty()) {
                tx.setVoucherType("Receipt");
            } else if (tx.getDebit() != null && !tx.getDebit().equals("-") && !tx.getDebit().isEmpty()) {
                tx.setVoucherType("Payment");
            } else {
                tx.setVoucherType("-");
            }
            transactionDTOS.add(tx);
        }
        return transactionDTOS;
    }

    /**
     * Check if a row is completely empty
     */
    private boolean isRowEmpty(List<String> row) {
        if (row == null || row.isEmpty()) {
            return true;
        }

        for (String cell : row) {
            if (cell != null && !cell.trim().isEmpty()) {
                return false; // Found at least one non-empty cell
            }
        }
        return true; // All cells are empty
    }

    /**
     * Check if row contains header keywords (in case there are multiple header rows)
     */
    private boolean containsHeaderKeywords(List<String> row) {
        if (row == null || row.isEmpty()) {
            return false;
        }

        // Common header keywords to skip
        String[] headerKeywords = {
                "date", "transaction date", "value date", "description", "particulars",
                "cheque", "ref no", "balance", "amount"
        };

        for (String cell : row) {
            if (cell != null) {
                String cellLower = cell.toLowerCase();
                for (String keyword : headerKeywords) {
                    if (cellLower.contains(keyword)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * 🚫 Skip rows containing "TOTAL"
     */
    private boolean isTotalRow(List<String> row) {
        if (row == null || row.isEmpty()) {
            return false;
        }
        for (String cell : row) {
            if (cell != null && cell.trim().equalsIgnoreCase("total")) {
                return true;
            }
        }
        return false;
    }


    //Federal Bank Extraction

    public List<List<String>> extractTableFederal(byte[] pdfBytes, String password) throws Exception {
        List<List<String>> tableData = new ArrayList<>();

        try (PDDocument pdfDocument = PDDocument.load(new ByteArrayInputStream(pdfBytes), password)) {
            ObjectExtractor extractor = new ObjectExtractor(pdfDocument);
            SpreadsheetExtractionAlgorithm sea = new SpreadsheetExtractionAlgorithm();

            for (int i = 1; i <= pdfDocument.getNumberOfPages(); i++) {
                Page page = extractor.extract(i);
                List<Table> tables = sea.extract(page);

                for (Table table : tables) {
                    for (List<RectangularTextContainer> row : table.getRows()) {

                        // Clean cell text
                        List<String> rowData = new ArrayList<>();
                        for (RectangularTextContainer cell : row) {
                            String cellText = cell.getText()
                                    .replaceAll("\\r|\\n", " ")
                                    .replaceAll("\\s{2,}", " ")
                                    .trim();
                            rowData.add(cellText);
                        }

                        // Join row text for pattern-based filtering
                        String joined = String.join(" ", rowData).toUpperCase();

                        // ✅ Skip unwanted content (headers, footers, disclaimers)
                        if (joined.isEmpty()
                                || joined.contains("FEDERAL BANK LTD")
                                || joined.contains("PAGE ")
                                || joined.contains("BRANCH:")
                                || joined.contains("WWW.FEDERALBANK.CO.IN")
                                || joined.contains("CIN:")
                                || joined.contains("STATEMENT OF ACCOUNT")
                                || joined.contains("CUSTOMER ID")
                                || joined.contains("ACCOUNT NUMBER")
                                || joined.contains("ACCOUNT STATUS")
                                || joined.contains("ABBREVIATIONS USED")
                                || joined.contains("DISCLAIMER")
                                || joined.contains("CASH : CASH TRANSACTION")
                                || joined.contains("TFR : TRANSFER TRANSACTION")
                                || joined.contains("FT : FUND TRANSFER")
                                || joined.contains("CLG : CLEARING TRANSACTION")
                                || joined.contains("SBINT : INTEREST ON SB ACCOUNT")
                                || joined.contains("MB : MOBILE BANKING")
                                || joined.contains("****END OF STATEMENT****")
                                || joined.contains("GRAND TOTAL")
                        ) {
                            continue; // skip header/footer/legend rows
                        }

                        tableData.add(rowData);
                    }
                }
            }
        }

        return tableData;
    }

    // Federal Bank Extraction
    //=========================

    public List<TransactionDTO> FederalBankMapDto(List<List<String>> tableRows) {
        List<TransactionDTO> transactions = new ArrayList<>();
        TransactionDTO lastTx = null;

        for (List<String> row : tableRows) {
            if (row.isEmpty() || row.get(0).toLowerCase().contains("date")) continue;

            // Handle multi-line description rows
            if (!row.get(0).matches("\\d{2}/\\d{2}/\\d{4}")) {
                if (lastTx != null) {
                    lastTx.setDescription(
                            (lastTx.getDescription() + " " + String.join(" ", row)).trim()
                    );
                }
                continue;
            }

            TransactionDTO tx = new TransactionDTO();

            tx.setTransactionDate(row.size() > 0 ? row.get(0).trim() : "-");
            tx.setDescription(row.size() > 2 ? row.get(2).trim() : "-");
            tx.setDebit(row.size() > 6 ? cleanAmount(row.get(6)) : "-");
            tx.setCredit(row.size() > 7 ? cleanAmount(row.get(7)) : "-");
            tx.setBalance(row.size() > 8 ? cleanAmount(row.get(8)) : "-");


            // 🧾 Voucher Type Logic
            if (tx.getCredit() != null && !tx.getCredit().equals("-") && !tx.getCredit().isEmpty()) {
                tx.setVoucherType("Receipt");
            } else if (tx.getDebit() != null && !tx.getDebit().equals("-") && !tx.getDebit().isEmpty()) {
                tx.setVoucherType("Payment");
            } else {
                tx.setVoucherType("-");
            }
            transactions.add(tx);
            lastTx = tx;
        }

        return transactions;
    }

    /**
     * 🧹 Clean description — remove unwanted footer/header parts even when mixed into same line
     */
    private String cleanDescription(String description) {
        if (description == null || description.isEmpty()) {
            return description;
        }

        String desc = description;

        // Remove everything after any footer-like patterns
        desc = desc.replaceAll("(?i)(PAGE\\s*\\d+.*|THE\\s+FEDERAL\\s+BANK.*|BRANCH:.*|CIN:.*|WEBSITE:.*|PH:.*|NAMAKKAL.*|ILLUPILI.*)", "")
                .replaceAll("\\s{2,}", " ") // collapse extra spaces
                .trim();

        // Also handle multi-line extracted text where footer starts in a new line
        String[] lines = desc.split("\\r?\\n");
        StringBuilder cleaned = new StringBuilder();
        for (String line : lines) {
            if (line.toUpperCase().matches(".*(PAGE\\s*\\d+|THE FEDERAL BANK|BRANCH:|CIN:|WEBSITE:|NAMAKKAL|ILLUPILI|DISCLAIMER).*")) {
                break; // stop at first footer line
            }
            cleaned.append(line.trim()).append(" ");
        }

        desc = cleaned.toString().trim();

        // Optional: remove email IDs or phone numbers if any remain
        desc = desc.replaceAll("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", "")
                .replaceAll("\\b\\d{2,}[-/]\\d{2,}[-/]\\d{2,}\\b", "")
                .replaceAll("\\b\\d{5,}\\b", "")
                .trim();

        return desc;
    }


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

    //Equitas Bank

    public List<TransactionDTO> equitasBankMapDto(List<List<String>> tableRows) {

        List<TransactionDTO> transactionDTOS = new ArrayList<>();

        if (tableRows == null || tableRows.isEmpty()) {
            return transactionDTOS;
        }

        double previousBalance = 0.0;
        boolean firstTransaction = true;

        for (List<String> row : tableRows) {

            if (row == null || row.size() < 2) {
                continue;
            }

            // Clean each cell
            for (int i = 0; i < row.size(); i++) {
                if (row.get(i) != null) {
                    row.set(i, row.get(i).replaceAll("[\\r\\n]", "").trim());
                }
            }

            if (isRowEmpty(row)) {
                continue;
            }

            String joined = String.join(" ", row).toLowerCase();

            // Skip headers
            if (joined.contains("txn date") ||
                    joined.contains("transaction date") ||
                    joined.contains("description") ||
                    joined.contains("debit") ||
                    joined.contains("credit") ||
                    joined.contains("balance")) {
                continue;
            }

            // Skip account details
            if (joined.contains("account number") ||
                    joined.contains("customer name") ||
                    joined.contains("branch") ||
                    joined.contains("ifsc") ||
                    joined.contains("statement period") ||
                    joined.contains("opening balance")) {
                continue;
            }

            if (joined.contains("total")) {
                continue;
            }

            if (!isDateLike(row.get(0))) {
                continue;
            }

            TransactionDTO tx = new TransactionDTO();
            tx.setTransactionDate(row.get(0));

            // Extract all amounts
            List<Double> amounts = new ArrayList<>();

            Matcher matcher = Pattern.compile("\\d+\\.\\d{2}").matcher(String.join(" ", row));

            while (matcher.find()) {
                amounts.add(Double.parseDouble(matcher.group()));
            }

            if (amounts.isEmpty()) {
                continue;
            }

            double balance = amounts.get(amounts.size() - 1);

            tx.setBalance(String.format("%.2f", balance));

            String debit = "-";
            String credit = "-";

            // ⭐ First transaction handling
            if (firstTransaction) {

                if (row.size() > 3) {
                    String possibleDebit = cleanAmount(row.get(3));
                    if (!possibleDebit.equals("-")) {
                        debit = possibleDebit;
                        tx.setVoucherType("Payment");
                    }
                }

                if (row.size() > 4) {
                    String possibleCredit = cleanAmount(row.get(4));
                    if (!possibleCredit.equals("-")) {
                        credit = possibleCredit;
                        tx.setVoucherType("Receipt");
                    }
                }

            } else {

                double difference = balance - previousBalance;

                if (difference > 0) {
                    credit = String.format("%.2f", difference);
                    tx.setVoucherType("Receipt");
                } else if (difference < 0) {
                    debit = String.format("%.2f", Math.abs(difference));
                    tx.setVoucherType("Payment");
                }
            }

            tx.setDebit(debit);
            tx.setCredit(credit);

            tx.setDescription(row.size() > 2 ? row.get(2) : "-");

            previousBalance = balance;
            firstTransaction = false;

            transactionDTOS.add(tx);
        }

        return transactionDTOS;
    }
    private boolean isDateLike(String value) {
        if (value == null) return false;

        return value.matches("\\d{1,2}[-/ ]\\d{1,2}[-/ ]\\d{2,4}") ||
                value.matches("\\d{1,2}-[A-Za-z]{3}-\\d{2,4}") ||
                value.matches("\\d{4}-\\d{1,2}-\\d{1,2}");
    }
}







