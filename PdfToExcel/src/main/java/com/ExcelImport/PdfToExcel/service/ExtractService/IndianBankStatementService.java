package com.ExcelImport.PdfToExcel.service.ExtractService;



import com.ExcelImport.PdfToExcel.dto.IndianBankTransactionDTO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;


import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class IndianBankStatementService {

    public List<IndianBankTransactionDTO> extractTransactionsAsDTO(byte[] pdfBytes) throws Exception {
        List<IndianBankTransactionDTO> transactions = new ArrayList<>();

        // 1️⃣ Load PDF and extract full text
        PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes));
        PDFTextStripper stripper = new PDFTextStripper();
        String text = stripper.getText(document);
        document.close();

        // 2️⃣ Isolate transaction section
        if (text.contains("ACCOUNT ACTIVITY")) {
            text = text.substring(text.indexOf("ACCOUNT ACTIVITY"));
        }

        // 3️⃣ Split into lines
        String[] lines = text.split("\\r?\\n");

        // 4️⃣ Patterns
        Pattern datePattern = Pattern.compile("^\\d{2}\\s[A-Za-z]{3}\\s\\d{4}");
        Pattern amountPattern = Pattern.compile("INR\\s?([\\d,]+\\.\\d{2})");

        List<String> currentBlock = new ArrayList<>();

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.contains("Date Transaction Details") || line.contains("Ending Balance")) continue;

            // Start new block when a date is found
            if (datePattern.matcher(line).find()) {
                if (!currentBlock.isEmpty()) {
                    transactions.add(convertBlockToDTO(currentBlock, datePattern, amountPattern));
                    currentBlock.clear();
                }
            }
            currentBlock.add(line);
        }
        if (!currentBlock.isEmpty()) {
            transactions.add(convertBlockToDTO(currentBlock, datePattern, amountPattern));
        }

        return transactions;
    }

    private IndianBankTransactionDTO convertBlockToDTO(List<String> block, Pattern datePattern, Pattern amountPattern) {
        String merged = String.join(" ", block);

        // Date
        Matcher dateMatcher = datePattern.matcher(merged);
        String date = dateMatcher.find() ? dateMatcher.group() : "";

        // Amounts
        Matcher amtMatcher = amountPattern.matcher(merged);
        List<String> amounts = new ArrayList<>();
        while (amtMatcher.find()) amounts.add(amtMatcher.group(1));

        String description = merged.replace(date, "")
                .replaceAll("INR\\s?[\\d,]+\\.\\d{2}", "")
                .replaceAll("\\s+", " ")
                .trim();

        String debit = "", credit = "", balance = "";
        if (amounts.size() >= 3) {
            debit = amounts.get(0);
            credit = amounts.get(1);
            balance = amounts.get(2);
        } else if (amounts.size() == 2) {
            debit = amounts.get(0);
            balance = amounts.get(1);
        } else if (amounts.size() == 1) {
            balance = amounts.get(0);
        }

        IndianBankTransactionDTO tx = new IndianBankTransactionDTO();
        tx.setTransactionDate(date);
        tx.setDescription(description);
        tx.setDebit(debit);
        tx.setCredit(credit);
        tx.setBalance(balance);

        return tx;
    }

}
