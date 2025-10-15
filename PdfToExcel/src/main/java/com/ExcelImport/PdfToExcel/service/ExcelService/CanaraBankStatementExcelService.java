package com.ExcelImport.PdfToExcel.service.ExcelService;

import com.ExcelImport.PdfToExcel.dto.CanaraBankTransactionDTO;
import com.ExcelImport.PdfToExcel.dto.KvbTransactionDTO;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.List;

@Service
public class CanaraBankStatementExcelService {

    public byte[] generateExcel(List<CanaraBankTransactionDTO> transactions) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Bank Statement");

            String[] headers = {"Txn Date", "Value Date", "BranchCode","Cheque No",
                    "Description", "Debit", "Credit", "Balance"};

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
            }

            int rowNum = 1;
            for (CanaraBankTransactionDTO tx : transactions) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(tx.getTransactionDate());
                row.createCell(1).setCellValue(tx.getValueDate());
                row.createCell(2).setCellValue(tx.getBranchCode());
                row.createCell(3).setCellValue(tx.getChequeNo());
                row.createCell(4).setCellValue(tx.getDescription());
                row.createCell(5).setCellValue(tx.getDebit());
                row.createCell(6).setCellValue(tx.getCredit());
                row.createCell(7).setCellValue(tx.getBalance());
            }

            for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                workbook.write(out);
                return out.toByteArray();
            }
        }
    }

}
