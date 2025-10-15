package com.ExcelImport.PdfToExcel.dto;

import lombok.*;

import java.util.List;
@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class HdfcBankTransactionDTO {
    private  String TransactionDate;
    private String valueDate;
    private String description;
    private String chequeNo;
    private String debit;
    private String credit;
    private String balance;
    private String branch;
    private String voucherName;
    private List<String> amounts;
}
