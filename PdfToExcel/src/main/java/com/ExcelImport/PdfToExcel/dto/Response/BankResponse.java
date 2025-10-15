package com.ExcelImport.PdfToExcel.dto.Response;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@Getter
@Setter
public class BankResponse {

    private String status;
    private String bank;
    private List<TransactionResponseDTO> transactions;


    public BankResponse(String status, String bank, List<TransactionResponseDTO> transactions) {
        this.status = status;
        this.bank = bank;
        this.transactions = transactions;
    }



}
