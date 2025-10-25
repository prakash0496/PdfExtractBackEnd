package com.ExcelImport.PdfToExcel.service.ExtractService;

import com.ExcelImport.PdfToExcel.dto.KvbTransactionDTO;
import com.ExcelImport.PdfToExcel.dto.Response.TransactionResponseDTO;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;


import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class KvbBankStatementService {

    // Step 1: Run OCR and return extracted text
    public String extractTextFromScannedPdf(byte[] pdfBytes) throws Exception {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);

            ITesseract tesseract = new Tesseract();
            tesseract.setDatapath("E:/PdfExtract/PdfToExcel/PdfToExcel/src/main/java/com/ExcelImport/PdfToExcel/tessdata");
            tesseract.setLanguage("eng");

            StringBuilder fullText = new StringBuilder();

            for (int page = 0; page < document.getNumberOfPages(); ++page) {
                BufferedImage image = pdfRenderer.renderImageWithDPI(page, 500);
                String extractedText = tesseract.doOCR(image);
                fullText.append(extractedText).append("\n");
            }

            return fullText.toString();
        }
    }

    public List<KvbTransactionDTO> extractTransactions(String ocrText) {
        List<KvbTransactionDTO> transactions = new ArrayList<>();
        List<List<String>> allTransactionAmounts = new ArrayList<>();

        String[] lines = ocrText.split("\\r?\\n");
        KvbTransactionDTO currentTx = null;
        StringBuilder descriptionBuilder = new StringBuilder();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // Clean OCR artifacts
            line = line.replace("|", " ")
                    .replace("=", "  ")
                    .replace("@", "")
                    .replaceAll("\\s+", " ")
                    .trim();

            // Skip headers or footers
            if (line.matches("(?i)^page\\s+no\\..*") || line.toLowerCase().startsWith("note")) {
                continue;
            }

            // Detect new transaction line (Transaction Date + Time)
            if (line.matches("^\\d{2}-\\d{2}-\\d{4}\\s+\\d{2}:\\d{2}:\\d{2}.*")) {

                // Save previous transaction
                if (currentTx != null) {
                    currentTx.setDescription(descriptionBuilder.toString().trim());
                    transactions.add(currentTx);
                }

                currentTx = new KvbTransactionDTO();
                descriptionBuilder = new StringBuilder();
                List<String> amounts = new ArrayList<>();

                String[] parts = line.split("\\s+");
                int idx = 0;

                try {
                    // Transaction Date + Time
                    currentTx.setTransactionDate(parts[idx] + " " + parts[idx + 1]);
                    idx += 2;

                    // Skip optional symbols (= or @)
                    while (idx < parts.length && (parts[idx].equals("=") || parts[idx].equals("@"))) {
                        idx++;
                    }

                    // Value Date
                    if (idx < parts.length && parts[idx].matches("\\d{2}-\\d{2}-\\d{4}")) {
                        currentTx.setValueDate(parts[idx]);
                        idx++;
                    } else {
                        currentTx.setValueDate("-");
                    }

                    // Branch Code (1–6 digits)
                    if (idx < parts.length && parts[idx].matches("\\d{1,6}")) {
                        currentTx.setBranch(parts[idx]);
                        idx++;
                    } else {
                        currentTx.setBranch("-");
                    }

                    // Cheque Number (10–18 digits)
                    if (idx < parts.length && parts[idx].matches("\\d{10,18}")) {
                        currentTx.setChequeNo(parts[idx]);
                        idx++;
                    } else {
                        currentTx.setChequeNo("-");
                    }

                    // Remaining tokens → Description + numeric amounts
                    for (; idx < parts.length; idx++) {
                        String token = parts[idx].replace(",", "").replace(")", "");
                        if (token.matches("\\d+(\\.\\d{1,2})?")) {
                            amounts.add(token);
                        } else {
                            descriptionBuilder.append(parts[idx]).append(" ");
                        }
                    }

                } catch (Exception e) {
                    System.out.println("⚠️ Error parsing line: " + line);
                }

                allTransactionAmounts.add(amounts);

            } else {
                // Multi-line description
                descriptionBuilder.append(line).append(" ");
            }
        }

        // Save last transaction
        if (currentTx != null) {
            currentTx.setDescription(descriptionBuilder.toString().trim());
            transactions.add(currentTx);
        }

        // 💰 Calculate Debit / Credit / Balance
        String previousBalance = null;

        for (int i = 0; i < transactions.size(); i++) {
            KvbTransactionDTO tx = transactions.get(i);
            List<String> amounts = allTransactionAmounts.get(i);

            tx.setDebit("-");
            tx.setCredit("-");
            tx.setBalance("-");

            if (!amounts.isEmpty()) {
                String balance = amounts.get(amounts.size() - 1);
                tx.setBalance(balance);

                if (i == 0 && amounts.size() >= 2) {
                    tx.setCredit(amounts.get(0));
                } else if (previousBalance != null) {
                    double prev = Double.parseDouble(previousBalance.replaceAll(",", ""));
                    double curr = Double.parseDouble(balance.replaceAll(",", ""));

                    if (curr > prev) {
                        tx.setCredit(String.format("%.2f", curr - prev));
                    } else if (curr < prev) {
                        tx.setDebit(String.format("%.2f", prev - curr));
                    }
                }
                previousBalance = balance;
            }

            // 🧾 Voucher Type Logic
            if (tx.getCredit() != null && !tx.getCredit().equals("-") && !tx.getCredit().isEmpty()) {
                tx.setVoucherName("Receipt");
            } else if (tx.getDebit() != null && !tx.getDebit().equals("-") && !tx.getDebit().isEmpty()) {
                tx.setVoucherName("Payment");
            } else {
                tx.setVoucherName("-");
            }
        }

        return transactions;
    }


    public List<TransactionResponseDTO> extractTransactionsAsDto(String ocrText) {
        List<KvbTransactionDTO> transactions = extractTransactions(ocrText);
        List<TransactionResponseDTO> dtoList = new ArrayList<>();

        for (KvbTransactionDTO tx : transactions) {
            dtoList.add(new TransactionResponseDTO(
                    tx.getTransactionDate(),
                    tx.getValueDate(),
                    tx.getChequeNo(),
                    tx.getBranch(),
                    tx.getDescription(),
                    tx.getDebit(),
                    tx.getCredit(),
                    tx.getBalance(),
                    tx.getVoucherName(),
                    tx.getLedgerName()
            ));
        }

        return dtoList;
    }

    // Ensure default values for missing amounts
    private void applyDefaultValues(KvbTransactionDTO tx) {
        if (tx.getDebit() == null || tx.getDebit().isEmpty()) tx.setDebit("-");
        if (tx.getCredit() == null || tx.getCredit().isEmpty()) tx.setCredit("-");
        if (tx.getBalance() == null || tx.getBalance().isEmpty()) tx.setBalance("-");
    }
}