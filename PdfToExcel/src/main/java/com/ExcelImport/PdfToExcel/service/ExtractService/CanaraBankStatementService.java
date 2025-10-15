package com.ExcelImport.PdfToExcel.service.ExtractService;

import com.ExcelImport.PdfToExcel.dto.CanaraBankTransactionDTO;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CanaraBankStatementService {


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
                    for (RectangularTextContainer cell : row) {
                        rowData.add(cell.getText().trim());
                    }
                    tableData.add(rowData);
                }
            }
        }

        pdfDocument.close();
        return tableData;
    }

    /**
     * Map raw table rows into DTOs
     */
    public List<CanaraBankTransactionDTO> mapTableToDto(List<List<String>> tableRows) {
        List<CanaraBankTransactionDTO> transactions = new ArrayList<>();

        for (List<String> row : tableRows) {

            // Skip header or invalid rows
            if (row.size() < 5 || row.get(0).toLowerCase().contains("txn")) continue;

            // Clean cells
            for (int i = 0; i < row.size(); i++) {
                row.set(i, row.get(i).replaceAll("[\\r\\n]", "").trim());
            }

            CanaraBankTransactionDTO tx = new CanaraBankTransactionDTO();

            // 🗓 Transaction and Value Dates
            tx.setTransactionDate(row.get(0));
            tx.setValueDate(row.size() > 1 ? row.get(1) : "-");

            // 🧾 Cheque Number & Description
            tx.setChequeNo(row.size() > 2 && !row.get(2).isEmpty() ? row.get(2) : "-");
            tx.setDescription(row.size() > 3 && !row.get(3).isEmpty() ? row.get(3) : "-");

            // 🏦 Extract and clean Branch Code (2–6 digits)
            String branchCell = row.size() > 4 && row.get(4) != null ? row.get(4).trim() : "-";
            Pattern p = Pattern.compile("\\b\\d{2,6}\\b");
            Matcher m = p.matcher(branchCell);
            String branchCode = m.find() ? m.group() : "-";
            tx.setBranchCode(branchCode);

            // 💰 Debit / Credit / Balance
            tx.setDebit(row.size() > 5 && !row.get(5).isEmpty() ? cleanAmount(row.get(5)) : "-");
            tx.setCredit(row.size() > 6 && !row.get(6).isEmpty() ? cleanAmount(row.get(6)) : "-");
            tx.setBalance(row.size() > 7 && !row.get(7).isEmpty() ? cleanAmount(row.get(7)) : "-");

            // 🧾 Voucher Type Logic
            if (tx.getCredit() != null && !tx.getCredit().equals("-") && !tx.getCredit().isEmpty()) {
                tx.setVoucherName("Receipt");
            } else if (tx.getDebit() != null && !tx.getDebit().equals("-") && !tx.getDebit().isEmpty()) {
                tx.setVoucherName("Payment");
            } else {
                tx.setVoucherName("-");
            }

            transactions.add(tx);
        }

        return transactions;
    }

    /**
     * Cleans numeric amount strings by removing commas and non-numeric characters.
     */
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
