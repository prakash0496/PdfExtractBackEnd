package com.ExcelImport.PdfToExcel.dto;

import lombok.*;

import java.util.List;
@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CanaraBankTransactionDTO {
    private String transactionDate;
    private String valueDate;
    private String chequeNo;
    private String description;
    private String branchCode;
    private String debit;
    private String credit;
    private String balance;
    private String voucherName;
    private String ledgerName;
}
