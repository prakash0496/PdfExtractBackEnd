package com.ExcelImport.PdfToExcel.service.OcrExtractService;

import com.ExcelImport.PdfToExcel.dto.Response.TransactionResponseDTO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.platform.commons.logging.Logger;
import org.junit.platform.commons.logging.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class BankStatementParser {


        private static final Logger logger = LoggerFactory.getLogger(BankStatementParser.class);

        public List<TransactionResponseDTO> extractTransactions(MultipartFile file) {
            try {
                String extractedText = extractTextFromPdf(file);
                return parseTransactionsFromText(extractedText);
            } catch (Exception e) {
//logger.error("Error extracting transactions from PDF", e);
                throw new RuntimeException("Failed to extract transactions", e);
            }
        }

        private String extractTextFromPdf(MultipartFile file) throws IOException {
            try (PDDocument document = PDDocument.load(file.getInputStream())) {
                PDFTextStripper stripper = new PDFTextStripper();
                return stripper.getText(document);
            }
        }

        private List<TransactionResponseDTO> parseTransactionsFromText(String text) {
            List<TransactionResponseDTO> transactions = new ArrayList<>();
            String[] lines = text.split("\n");

            boolean inTransactionSection = false;

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();

                // Detect start of transaction section
                if (isTransactionHeader(line)) {
                    inTransactionSection = true;
                    continue;
                }

                // Detect end of transaction section
                if (inTransactionSection && isSectionEnd(line)) {
                    inTransactionSection = false;
                    break;
                }

                // Parse transaction lines
                if (inTransactionSection && !line.isEmpty() && isTransactionData(line)) {
                    TransactionResponseDTO transaction = parseTransactionLine(line);
                    if (transaction != null) {
                        transactions.add(transaction);
                    }
                }
            }

          //  logger.info("Extracted {} transactions", transactions.size());
            return transactions;
        }

        private boolean isTransactionHeader(String line) {
            String lowerLine = line.toLowerCase();
            return lowerLine.contains("date") &&
                    (lowerLine.contains("description") || lowerLine.contains("particulars")) &&
                    (lowerLine.contains("amount") || lowerLine.contains("debit") || lowerLine.contains("credit"));
        }

        private boolean isSectionEnd(String line) {
            String lowerLine = line.toLowerCase();
            return lowerLine.contains("total") ||
                    lowerLine.contains("balance") ||
                    lowerLine.contains("summary") ||
                    lowerLine.matches(".*page.*\\d+.*of.*\\d+.*");
        }

        private boolean isTransactionData(String line) {
            // Check if line contains date pattern and amount pattern
            boolean hasDate = line.matches(".*\\d{1,2}[-/]\\d{1,2}[-/]\\d{2,4}.*") ||
                    line.matches(".*\\d{1,2}[-/][A-Za-z]{3}[-/]\\d{2,4}.*");

            boolean hasAmount = line.matches(".*\\d+[.,]\\d{2}.*");

            return hasDate && hasAmount;
        }

        private TransactionResponseDTO parseTransactionLine(String line) {
            try {
                // Clean the line - replace multiple spaces with single delimiter
                String cleanedLine = line.replaceAll("\\s{2,}", "|");
                String[] parts = cleanedLine.split("\\|");

                if (parts.length >= 4) {
                    TransactionResponseDTO transaction = new TransactionResponseDTO();

                    // Parse based on number of parts
                    if (parts.length >= 6) {
                        // Assume format: TransDate | ValueDate | Description | Debit | Credit | Balance
                        transaction.setTransactionDate(parts[0].trim());
                        transaction.setValueDate(parts[1].trim());
                        transaction.setDescription(parts[2].trim());
                        transaction.setDebit(extractAmount(parts[3]));
                        transaction.setCredit(extractAmount(parts[4]));
                        transaction.setBalance(extractAmount(parts[5]));
                    } else if (parts.length == 5) {
                        // Assume format: TransDate | Description | Debit | Credit | Balance
                        transaction.setTransactionDate(parts[0].trim());
                        transaction.setValueDate(parts[0].trim()); // Same as transaction date
                        transaction.setDescription(parts[1].trim());
                        transaction.setDebit(extractAmount(parts[2]));
                        transaction.setCredit(extractAmount(parts[3]));
                        transaction.setBalance(extractAmount(parts[4]));
                    } else if (parts.length == 4) {
                        // Assume format: Date | Description | Amount | Balance
                        transaction.setTransactionDate(parts[0].trim());
                        transaction.setValueDate(parts[0].trim()); // Same as transaction date
                        transaction.setDescription(parts[1].trim());

                        String amount = extractAmount(parts[2]);
                        // Determine if amount is debit or credit
                        if (amount.startsWith("-") || isLikelyDebit(parts[1])) {
                            transaction.setDebit(amount.replace("-", ""));
                            transaction.setCredit("");
                        } else {
                            transaction.setDebit("");
                            transaction.setCredit(amount);
                        }
                        transaction.setBalance(extractAmount(parts[3]));
                    }

                    // Validate that we have at least some data
                    if (isValidTransaction(transaction)) {
                        return transaction;
                    }
                }
            } catch (Exception e) {
//                logger.warn("Failed to parse transaction line: {}", line);
            }
            return null;
        }

        private String extractAmount(String text) {
            if (text == null || text.trim().isEmpty()) {
                return "";
            }

            // Match currency patterns: 1,234.56, 1234.56, 1.234,56, $123.45, etc.
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[-]?[\\$€£]?[\\d.,]+\\d{2}");
            java.util.regex.Matcher matcher = pattern.matcher(text);

            if (matcher.find()) {
                String amount = matcher.group();
                // Standardize format: remove currency symbols and ensure decimal point
                amount = amount.replaceAll("[\\$€£]", "").replace(",", "");
                return amount;
            }

            return "";
        }

        private boolean isLikelyDebit(String description) {
            String descLower = description.toLowerCase();
            return descLower.contains("withdrawal") ||
                    descLower.contains("debit") ||
                    descLower.contains("payment") ||
                    descLower.contains("charge") ||
                    descLower.contains("fee");
        }

        private boolean isValidTransaction(TransactionResponseDTO transaction) {
            return transaction.getTransactionDate() != null &&
                    !transaction.getTransactionDate().isEmpty() &&
                    transaction.getDescription() != null &&
                    !transaction.getDescription().isEmpty() &&
                    (transaction.getDebit() != null || transaction.getCredit() != null);
        }
    }


