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
import java.util.ArrayList;
import java.util.List;

@Service
public class StateBankStatementService {

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

    public List<StateBankTransactionDTO> mapTableToDto(List<List<String>> tableRows){
        List<StateBankTransactionDTO> transactionDTOS = new ArrayList<>();

        for (List<String> row : tableRows){

            // Skip if row is empty or has too few columns
            if (isRowEmpty(row) || row.size() < 5 || row.get(0).toLowerCase().contains("post")) {
                continue;
            }

            // Clean up each cell in the row
            for (int i = 0; i < row.size(); i++) {
                row.set(i, row.get(i).replaceAll("[\\r\\n]", "").trim());
            }

            // Skip if after cleaning, the row is empty
            if (isRowEmpty(row)) {
                continue;
            }

            StateBankTransactionDTO tx = new StateBankTransactionDTO();

            tx.setTransactionDate(row.get(0));
            tx.setValueDate(row.size() > 1 ? row.get(1) : "-" );
            tx.setDescription(row.size() > 2 && !row.get(2).isEmpty() ? row.get(2) : "-");
            tx.setChequeNo(row.size() > 3 && !row.get(3).isEmpty() ? row.get(3) : "-");
            tx.setDebit(row.size() > 4 && !row.get(4).isEmpty() ? cleanAmount(row.get(4)) : "-");
            tx.setCredit(row.size() > 5 && !row.get(5).isEmpty() ? cleanAmount(row.get(5)) : "-");
            tx.setBalance(row.size() > 6 && !row.get(6).isEmpty() ? cleanAmount(row.get(6)) : "-");

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

    private String cleanAmount(String value) {
        if (value == null || value.trim().isEmpty() || value.equals("-")) return "-";
        return value.replace(",", "").replaceAll("[^0-9.]", "").trim();
    }

    // Ensure default values for missing amounts
    private void applyDefaultValues(CanaraBankTransactionDTO tx) {
        if (tx.getDebit() == null || tx.getDebit().isEmpty()) tx.setDebit("-");
        if (tx.getCredit() == null || tx.getCredit().isEmpty()) tx.setCredit("-");
        if (tx.getBalance() == null || tx.getBalance().isEmpty()) tx.setBalance("-");
    }
}