package com.ExcelImport.PdfToExcel.service.ExtractService;

import com.ExcelImport.PdfToExcel.dto.CityUnionBankTransactionDTO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;
import technology.tabula.ObjectExtractor;
import technology.tabula.Page;
import technology.tabula.RectangularTextContainer;
import technology.tabula.Table;
import technology.tabula.extractors.SpreadsheetExtractionAlgorithm;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class CityUnionBankStatementService {

    /**
     * Extract raw table data from PDF using Tabula
     */
    public List<List<String>> extractTableFromPdf(byte[] pdfBytes) throws Exception {
        List<List<String>> tableData = new ArrayList<>();

        PDDocument pdfDocument = PDDocument.load(new ByteArrayInputStream(pdfBytes));
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

    public List<CityUnionBankTransactionDTO> mapTableToDto(List<List<String>> tableRows){
        List<CityUnionBankTransactionDTO> transactionDTOS = new ArrayList<>();

        boolean isFirstRow = true;

        for (List<String> row : tableRows){

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
            if (isRowEmpty(row) || containsHeaderKeywords(row)) {
                continue;
            }

            CityUnionBankTransactionDTO tx = new CityUnionBankTransactionDTO();

            tx.setTransactionDate(row.get(0));
            tx.setDescription(row.size() > 1 && !row.get(1).isEmpty() ? row.get(1) : "-");
            tx.setChequeNo(row.size() > 2 && !row.get(2).isEmpty() ? row.get(2) : "-");
            tx.setDebit(row.size() > 3 && !row.get(3).isEmpty() ? cleanAmount(row.get(3)) : "-");
            tx.setCredit(row.size() > 4 && !row.get(4).isEmpty() ? cleanAmount(row.get(4)) : "-");
            tx.setBalance(row.size() > 5 && !row.get(5).isEmpty() ? cleanAmount(row.get(5)) : "-");

            // 🧾 Voucher Type Logic
            if (tx.getCredit() != null && !tx.getCredit().equals("-") && !tx.getCredit().isEmpty()) {
                tx.setVoucherName("Receipt");
            } else if (tx.getDebit() != null && !tx.getDebit().equals("-") && !tx.getDebit().isEmpty()) {
                tx.setVoucherName("Payment");
            } else {
                tx.setVoucherName("-");
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
                "cheque", "ref no", "debit", "credit", "balance", "amount"
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

    private String cleanAmount(String value) {
        if (value == null || value.trim().isEmpty() || value.equals("-")) return "-";
        return value.replace(",", "").replaceAll("[^0-9.]", "").trim();
    }
}