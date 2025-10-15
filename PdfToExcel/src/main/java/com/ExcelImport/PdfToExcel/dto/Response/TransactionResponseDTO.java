package com.ExcelImport.PdfToExcel.dto.Response;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponseDTO {

        private String transactionDate;
        private String valueDate;
        private String chequeNo;
        private String branchCode;
        private String description;
        private String debit;
        private String credit;
        private String balance;
        private String voucherName;
        private String ledgerName;
    }






