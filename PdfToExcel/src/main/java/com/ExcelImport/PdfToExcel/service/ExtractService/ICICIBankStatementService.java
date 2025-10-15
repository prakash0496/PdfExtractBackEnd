package com.ExcelImport.PdfToExcel.service.ExtractService;

import com.ExcelImport.PdfToExcel.dto.ICICIBankTransactionDTO;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ICICIBankStatementService {

    public List<ICICIBankTransactionDTO> extractTransaction(String ocrText) {
        List<ICICIBankTransactionDTO> transactions = new ArrayList<>();
        List<List<String>> allTransactionAmounts = new ArrayList<>();

        // 🧹 Remove ICICI Bank disclaimers and unwanted text blocks before processing
        ocrText = ocrText
                .replaceAll("(?is)ACCOUNT\\s+TYPE\\s+ACCOUNT\\s+NUMBER.*?This\\s+is\\s+an\\s+authenticated\\s+intimation/statement\\.", "")
                .replaceAll("(?is)Savings\\s+X{4,}.*?This\\s+is\\s+a\\s+system\\s+generated\\s+statement\\.", "")
                .replaceAll("(?is)Pradhan\\s+Mantri\\s+Jan\\s+Dhan\\s+Yojana.*?www\\.icicibank\\.com.*?(?=\\d{2}-\\d{2}-\\d{4}|$)", "")
                .replaceAll("(?is)Registered\\s+Office:.*?(?=\\d{2}-\\d{2}-\\d{4}|$)", "")
                .replaceAll("(?is)Do\\s+not\\s+fall\\s+prey\\s+to\\s+fictitious\\s+offers.*?(?=\\d{2}-\\d{2}-\\d{4}|$)", "")
                .replaceAll("(?is)PMJJBY\\s+Insurance.*?(?=\\d{2}-\\d{2}-\\d{4}|$)", "")
                .replaceAll("(?is)DICGC.*?(?=\\d{2}-\\d{2}-\\d{4}|$)", "")
                .replaceAll("(?is)Registration\\s+No\\..*?(?=\\d{2}-\\d{2}-\\d{4}|$)", "")
                .replaceAll("(?is)CIN\\s*[:].*?(?=\\d{2}-\\d{2}-\\d{4}|$)", "")
                .replaceAll("(?is)Page\\s+\\d+\\s+of\\s+\\d+.*?(?=\\d{2}-\\d{2}-\\d{4}|$)", "")
                .replaceAll("(?is)https://www\\.icicibank\\.com.*?(?=\\d{2}-\\d{2}-\\d{4}|$)", "");

        String[] lines = ocrText.split("\\r?\\n");
        ICICIBankTransactionDTO currentTx = null;
        StringBuilder descriptionBuilder = new StringBuilder();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // Clean OCR artifacts
            line = line.replace("|", " ")
                    .replace("=", " ")
                    .replace("@", "")
                    .replaceAll("\\s+", " ")
                    .trim();

            // ⛔ Skip unwanted footer/header lines
            if (line.matches("(?i).*Total:.*") ||
                    line.matches("(?i).*Statement of Transactions.*") ||
                    line.matches("(?i).*Account Related Other Information.*") ||
                    line.matches("(?i).*Savings\\s+X{4,}.*") ||
                    line.matches("(?i).*This is a system generated statement.*") ||
                    line.matches("(?i).*Pradhan Mantri Jan Dhan Yojana.*") ||
                    line.matches("(?i).*Do not fall prey to fictitious offers.*") ||
                    line.matches("(?i).*Bank’s Code of Commitment.*") ||
                    line.matches("(?i).*Registered Office: ICICI Bank Tower.*") ||
                    line.matches("(?i).*Customers are requested to immediately notify.*") ||
                    line.matches("(?i).*https://www.icicibank\\.com.*") ||
                    line.matches("(?i).*CIN\\s*:.*") ||
                    line.matches("(?i).*Page\\s+\\d+\\s+of\\s+\\d+.*")) {
                continue;
            }

            // Detect start of new transaction (date pattern)
            if (line.matches("^\\d{2}-\\d{2}-\\d{4}.*")) {
                // Save previous transaction if exists
                if (currentTx != null) {
                    String desc = descriptionBuilder.toString().trim();
                    desc = desc.replaceAll(
                            "(?is)ACCOUNT\\s+TYPE\\s+ACCOUNT\\s+NUMBER.*?This\\s+is\\s+an\\s+authenticated\\s+intimation/statement\\.",
                            ""
                    ).trim(); // 🧹 clean unwanted content from description
                    currentTx.setDescription(desc);
                    transactions.add(currentTx);
                }

                currentTx = new ICICIBankTransactionDTO();
                descriptionBuilder = new StringBuilder();
                List<String> amounts = new ArrayList<>();

                String[] parts = line.split("\\s+");
                int idx = 0;

                try {
                    // Transaction date
                    currentTx.setTransactionDate(parts[idx]);
                    idx++;

                    // Skip junk symbols
                    while (idx < parts.length && (parts[idx].equals("=") || parts[idx].equals("@"))) {
                        idx++;
                    }

                    // Extract description and numeric values
                    for (; idx < parts.length; idx++) {
                        String token = parts[idx].replace(",", "").replace(")", "");
                        if (token.matches("\\d+(\\.\\d{1,2})?")) {
                            amounts.add(token);
                        } else {
                            descriptionBuilder.append(parts[idx]).append(" ");
                        }
                    }

                } catch (Exception e) {
                    System.out.println("Error parsing line: " + line);
                }

                allTransactionAmounts.add(amounts);
            } else {
                // continuation of description
                descriptionBuilder.append(line).append(" ");
            }
        }

        // Add last transaction
        if (currentTx != null) {
            String desc = descriptionBuilder.toString().trim();
            desc = desc.replaceAll(
                    "(?is)ACCOUNT\\s+TYPE\\s+ACCOUNT\\s+NUMBER.*?This\\s+is\\s+an\\s+authenticated\\s+intimation/statement\\.",
                    ""
            ).trim();
            currentTx.setDescription(desc);
            transactions.add(currentTx);
        }

        // Assign debit/credit/balance logic
        String previousBalance = null;
        for (int i = 0; i < transactions.size(); i++) {
            ICICIBankTransactionDTO tx = transactions.get(i);
            List<String> amounts = (i < allTransactionAmounts.size()) ? allTransactionAmounts.get(i) : new ArrayList<>();

            tx.setDebit("-");
            tx.setCredit("-");
            tx.setBalance("-");

            if (!amounts.isEmpty()) {
                String balance = amounts.get(amounts.size() - 1);
                tx.setBalance(balance);

                try {
                    double curr = Double.parseDouble(balance.replaceAll(",", ""));
                    if (i == 0 && amounts.size() >= 2) {
                        tx.setCredit(amounts.get(0));
                    } else if (previousBalance != null) {
                        double prev = Double.parseDouble(previousBalance.replaceAll(",", ""));
                        double diff = curr - prev;
                        if (diff > 0) tx.setCredit(String.format("%.2f", diff));
                        else if (diff < 0) tx.setDebit(String.format("%.2f", Math.abs(diff)));
                    }
                    previousBalance = balance;
                } catch (NumberFormatException e) {
                    System.out.println("Invalid balance value for: " + tx.getDescription());
                }
            }

            // Voucher type logic
            if (!"-".equals(tx.getCredit())) tx.setVoucherName("Receipt");
            else if (!"-".equals(tx.getDebit())) tx.setVoucherName("Payment");
            else tx.setVoucherName("-");
        }

        return transactions;
    }


    public String extractTextFromPdf(byte[] pdfBytes) throws Exception {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            PDFTextStripper pdfStripper = new PDFTextStripper();
            pdfStripper.setSortByPosition(true); // Important for table data
            return pdfStripper.getText(document);
        }
    }
}



