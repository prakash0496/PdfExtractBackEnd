package com.ExcelImport.PdfToExcel.dto.Response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDTO {

    private String transactionDate;
    private String description;
    private String debit;
    private String credit;
    private String balance;
    private String voucherType;
    private String valueDate;

}
