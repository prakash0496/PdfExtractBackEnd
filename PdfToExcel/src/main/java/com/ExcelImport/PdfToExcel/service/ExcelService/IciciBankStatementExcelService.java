package com.ExcelImport.PdfToExcel.service.ExcelService;


import com.ExcelImport.PdfToExcel.dto.ICICIBankTransactionDTO;
import com.ExcelImport.PdfToExcel.dto.Response.TransactionDTO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
public class IciciBankStatementExcelService {

    public byte[] generateExcel(List<TransactionDTO> transactions) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Bank Statement");

            String[] headers = {"Transaction Date","Description",
                     "Debit", "Credit", "Balance"};

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            int rowNum = 1;
            for (TransactionDTO tx : transactions) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(tx.getTransactionDate());
                row.createCell(1).setCellValue(tx.getDescription());
                row.createCell(2).setCellValue(tx.getDebit());
                row.createCell(3).setCellValue(tx.getCredit());
                row.createCell(4).setCellValue(tx.getBalance());
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        }
    }
}
