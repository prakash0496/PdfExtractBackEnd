package com.ExcelImport.PdfToExcel.service.ExtractService;

import com.ExcelImport.PdfToExcel.dto.CanaraBankTransactionDTO;
import com.ExcelImport.PdfToExcel.dto.StateBankTransactionDTO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

import java.io.ByteArrayInputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
public class StateBankStatementService {

    /**
     * Extract raw table data from PDF using Tabula
     */
    public List<List<String>> extractTableFromPdf(byte[] pdfBytes,String password) throws Exception {
        List<List<String>> tableData = new ArrayList<>();

        PDDocument pdfDocument = PDDocument.load(new ByteArrayInputStream(pdfBytes),password);
        ObjectExtractor extractor = new ObjectExtractor(pdfDocument);
        SpreadsheetExtractionAlgorithm sea = new SpreadsheetExtractionAlgorithm();

        // Iterate through all pages
        for (int i = 1; i <= pdfDocument.getNumberOfPages(); i++) {
            Page page = extractor.extract(i);
            List<Table> tables = sea.extract(page);

            for (Table table : tables) {
                for (List<RectangularTextContainer> row : table.getRows()) {
                    List<String> rowData = new ArrayList<>();
                    boolean isEmptyRow = true;

                    for (RectangularTextContainer cell : row) {
                        String cellText = cell.getText().trim();
                        rowData.add(cellText);
                        // Check if any cell in the row has content
                        if (!cellText.isEmpty()) {
                            isEmptyRow = false;
                        }
                    }

                    // Only add non-empty rows
                    if (!isEmptyRow) {
                        tableData.add(rowData);
                    }
                }
            }
        }

        pdfDocument.close();
        return tableData;
    }

    public List<StateBankTransactionDTO> mapTableToDto(List<List<String>> tableRows) {
        List<StateBankTransactionDTO> transactions = new ArrayList<>();

        for (List<String> row : tableRows) {
            if (row == null || row.isEmpty()) continue;

            // ✅ Clean up each cell properly
            for (int i = 0; i < row.size(); i++) {
                String cell = row.get(i);
                row.set(i, (cell == null ? "" : cell.replaceAll("[\\r\\n]", "").trim()));
            }

            // ✅ Skip header or invalid rows
            if (row.get(0).toLowerCase().contains("txn") || row.get(0).toLowerCase().contains("date")) continue;

            // 🚫 Skip "TOTAL" rows
            if (isCloseRow(row)) {
                continue;
            }

            StateBankTransactionDTO dto = new StateBankTransactionDTO();

            // ✅ Detect if Branch Code column exists
            // Format A: TxnDate, ValueDate, Description, RefNo/ChequeNo, BranchCode, Debit, Credit, Balance
            // Format B: TxnDate, ValueDate, Description, RefNo/ChequeNo, Debit, Credit, Balance
            boolean hasBranchCode = row.size() >= 8;

            dto.setTransactionDate(formatTallyDate(getValue(row, 0)));  // 🟩 formatted for Tally
            dto.setValueDate(formatTallyDate(getValue(row, 1)));
            dto.setDescription(getValue(row, 2));
            dto.setChequeNo(getValue(row, 3));

            if (hasBranchCode) {
                dto.setBranchCode(getValue(row, 4));
                dto.setDebit(cleanAmount(getValue(row, 5)));
                dto.setCredit(cleanAmount(getValue(row, 6)));
                dto.setBalance(cleanAmount(getValue(row, 7)));
            } else {
                dto.setBranchCode("-");
                dto.setDebit(cleanAmount(getValue(row, 4)));
                dto.setCredit(cleanAmount(getValue(row, 5)));
                dto.setBalance(cleanAmount(getValue(row, 6)));
            }

            // ✅ Voucher logic
            if (!dto.getCredit().equals("-")) dto.setVoucherName("Receipt");
            else if (!dto.getDebit().equals("-")) dto.setVoucherName("Payment");
            else dto.setVoucherName("-");

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
        try {
            if (dateStr == null || dateStr.trim().isEmpty() || dateStr.equalsIgnoreCase("-")) {
                return "-";
            }

            // Normalize separators (handles both "/" and "-")
            dateStr = dateStr.trim().replace("-", "/");

            SimpleDateFormat inputDate = new SimpleDateFormat("dd/MM/yyyy");
            SimpleDateFormat tallyDate = new SimpleDateFormat("yyyy-MM-dd");

            Date parsedDate = inputDate.parse(dateStr);
            return tallyDate.format(parsedDate);  // ✅ e.g., "20240401"
        } catch (ParseException e) {
            return "-";  // Graceful fallback
        }

    }

    /**
     * 🚫 Skip rows containing "TOTAL"
     */
    private boolean isCloseRow(List<String> row) {
        if (row == null || row.isEmpty()) {
            return false;
        }
        for (String cell : row) {
            if (cell != null && cell.trim().equalsIgnoreCase("closing balance")) {
                return true;
            }
        }
        return false;
    }


}
