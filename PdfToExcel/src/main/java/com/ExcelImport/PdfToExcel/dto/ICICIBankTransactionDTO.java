package com.ExcelImport.PdfToExcel.dto;

import lombok.*;

import java.util.List;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ICICIBankTransactionDTO {
    private  String TransactionDate;
    private String valueDate;
    private String description;
    private String chequeNo;
    private String debit;
    private String credit;
    private String balance;
    private String branchCode;
    private String voucherName;
    private String ledgerName;
    private String autoSweep;
    private String reverseSweep;
    private List<String> amounts;
}
