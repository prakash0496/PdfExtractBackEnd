package com.ExcelImport.PdfToExcel.dto.Response;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class UniverseResponse {

    private  String status;
    private String bank;
    private List<TransactionDTO> transactions;

    public UniverseResponse(String status,String bank,List<TransactionDTO> transactions){
        this.status=status;
        this.bank = bank;
        this.transactions=transactions;
    }

}
