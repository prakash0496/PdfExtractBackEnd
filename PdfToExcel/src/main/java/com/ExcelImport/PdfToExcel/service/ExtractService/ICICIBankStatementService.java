package com.ExcelImport.PdfToExcel.service.ExtractService;

import com.ExcelImport.PdfToExcel.dto.ICICIBankTransactionDTO;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ICICIBankStatementService {

    public List<ICICIBankTransactionDTO> extractUsingTabula(byte[] pdfBytes) {
        List<ICICIBankTransactionDTO> transactions = new ArrayList<>();

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

                        ICICIBankTransactionDTO tx = new ICICIBankTransactionDTO();
                        tx.setTransactionDate(first);
                        tx.setDescription(description);
                        tx.setChequeNo(chequeNo);
                        tx.setDebit(debit > 0 ? String.format("%.2f", debit) : "-");
                        tx.setCredit(credit > 0 ? String.format("%.2f", credit) : "-");
                        tx.setBalance(balance);

                        if (debit > 0)
                            tx.setVoucherName("Payment");
                        else if (credit > 0)
                            tx.setVoucherName("Receipt");
                        else
                            tx.setVoucherName("-");

                        transactions.add(tx);
                    }
                }
            }

        } catch (Exception e) {
            System.out.println("⚠️ Tabula extraction error: " + e.getMessage());
        }

        return transactions;
    }


    private double parseAmount(String amountText) {
        if (amountText == null || amountText.trim().isEmpty()) return 0.0;
        try {
            return Double.parseDouble(amountText.replaceAll("[^0-9.]", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private String cleanAmount(String value) {
        if (value == null || value.trim().isEmpty() || value.equals("-")) return "-";
        return value.replace(",", "").replaceAll("[^0-9.]", "").trim();
    }
}




