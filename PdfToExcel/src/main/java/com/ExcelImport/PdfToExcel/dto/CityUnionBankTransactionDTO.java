package com.ExcelImport.PdfToExcel.dto;


import lombok.*;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CityUnionBankTransactionDTO {
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
