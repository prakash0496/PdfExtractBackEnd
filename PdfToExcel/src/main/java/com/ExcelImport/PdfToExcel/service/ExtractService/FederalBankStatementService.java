package com.ExcelImport.PdfToExcel.service.ExtractService;

import com.ExcelImport.PdfToExcel.dto.FederalBankTransactionDTO;
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
public class FederalBankStatementService {

    public List<List<String>> extractTableFromPdf(byte[] pdfBytes) throws Exception {
        List<List<String>> tableData = new ArrayList<>();

        try (PDDocument pdfDocument = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
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




    public List<FederalBankTransactionDTO> mapFederalTableToDto(List<List<String>> tableRows) {
        List<FederalBankTransactionDTO> transactions = new ArrayList<>();
        FederalBankTransactionDTO lastTx = null;

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

            FederalBankTransactionDTO tx = new FederalBankTransactionDTO();

            tx.setTransactionDate(row.size() > 0 ? row.get(0).trim() : "-");
            tx.setValueDate(row.size() > 1 ? row.get(1).trim() : "-");
            tx.setDescription(row.size() > 2 ? row.get(2).trim() : "-");
            tx.setChequeNo(row.size() > 5 ? row.get(5).trim() : "-");
            tx.setDebit(row.size() > 6 ? cleanAmount(row.get(6)) : "-");
            tx.setCredit(row.size() > 7 ? cleanAmount(row.get(7)) : "-");
            tx.setBalance(row.size() > 8 ? cleanAmount(row.get(8)) : "-");

            // 🧾 Voucher Type Logic
            if (tx.getCredit() != null && !tx.getCredit().equals("-") && !tx.getCredit().isEmpty()) {
                tx.setVoucherName("Receipt");
            } else if (tx.getDebit() != null && !tx.getDebit().equals("-") && !tx.getDebit().isEmpty()) {
                tx.setVoucherName("Payment");
            } else {
                tx.setVoucherName("-");
            }
            transactions.add(tx);
            lastTx = tx;
        }

        return transactions;
    }

    /**
     * Utility to clean numeric amount strings (remove commas and stray symbols)
     */
    private String cleanAmount(String value) {
        if (value == null || value.trim().isEmpty() || value.equals("-")) return "-";
        return value.replace(",", "").replaceAll("[^0-9.]", "").trim();
    }

}
