package com.ExcelImport.PdfToExcel.service.TallyService;

import com.ExcelImport.PdfToExcel.dto.CanaraBankTransactionDTO;
import com.ExcelImport.PdfToExcel.dto.KvbTransactionDTO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;


@Service
public class TallyConversionService {

    public byte[] generateTallyXml(String jsonData, String bankName,String typeBank) throws Exception {
        StringBuilder xml = new StringBuilder();
        xml.append("<ENVELOPE>\n")
                .append("<HEADER><TALLYREQUEST>Import Data</TALLYREQUEST></HEADER>\n")
                .append("<BODY><IMPORTDATA>\n")
                .append("<REQUESTDESC><REPORTNAME>Vouchers</REPORTNAME></REQUESTDESC>\n")
                .append("<REQUESTDATA>\n");

        ObjectMapper mapper = new ObjectMapper();
        List<Map<String, Object>> transactions = mapper.readValue(jsonData, new TypeReference<>(){});

        int counter = 1;
        SimpleDateFormat inputDate = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        SimpleDateFormat tallyDate = new SimpleDateFormat("yyyyMMdd");

        for (Map<String, Object> tx : transactions) {
            String date = (String) tx.get("transactionDate");
            String narration = (String) tx.get("description");
            String debit = (String) tx.get("debit");
            String credit = (String) tx.get("credit");
            String voucherName = (String) tx.get("voucherName");
            String ledgerName = typeBank;

            boolean isCredit = credit != null && !credit.equals("-") && !credit.isEmpty();
            String amount = isCredit ? credit : debit;
            if (amount == null || amount.equals("-") || amount.isEmpty()) continue;

            amount = amount.replaceAll(",", "").trim();
            String formattedDate;
            try {
                formattedDate = tallyDate.format(new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").parse(date));
            } catch (ParseException e) {
                formattedDate = tallyDate.format(new SimpleDateFormat("dd-MM-yyyy").parse(date));
            }


            String voucherNumber = String.valueOf(counter);
            String guid = "GUID-" + voucherNumber;

                xml.append("<TALLYMESSAGE xmlns:UDF=\"TallyUDF\">\n")
                    .append("<VOUCHER VCHTYPE=\"").append(isCredit ? "Receipt" : "Payment")
                    .append("\" ACTION=\"Create\" OBJVIEW=\"Accounting Voucher View\">\n")
                    .append("<GUID>").append(guid).append("</GUID>\n")
                    .append("<DATE>").append(formattedDate).append("</DATE>\n")
                    .append("<VOUCHERNUMBER>").append(voucherNumber).append("</VOUCHERNUMBER>\n")
                    .append("<NARRATION>").append(escapeXml(narration)).append("</NARRATION>\n")
                    .append("<VOUCHERTYPENAME>").append(escapeXml(voucherName)).append("</VOUCHERTYPENAME>\n")
                    .append("<PARTYLEDGERNAME>").append(bankName).append("</PARTYLEDGERNAME>\n")
                    .append("<PERSISTEDVIEW>Accounting Voucher View</PERSISTEDVIEW>\n")

                    // Suspense Ledger
                    .append("<ALLLEDGERENTRIES.LIST>\n")
                    .append("<LEDGERNAME>SUSPENSE</LEDGERNAME>\n")
                    .append("<ISDEEMEDPOSITIVE>").append(isCredit ? "No" : "Yes").append("</ISDEEMEDPOSITIVE>\n")
                    .append("<AMOUNT>").append(isCredit ? amount : "-" + amount).append("</AMOUNT>\n")
                    .append("</ALLLEDGERENTRIES.LIST>\n")

                    // Bank Ledger
                    .append("<ALLLEDGERENTRIES.LIST>\n")
                    .append("<LEDGERNAME>").append(bankName).append("</LEDGERNAME>\n")
                    .append("<ISDEEMEDPOSITIVE>").append(isCredit ? "Yes" : "No").append("</ISDEEMEDPOSITIVE>\n")
                    .append("<AMOUNT>").append(isCredit ? "-" + amount : amount).append("</AMOUNT>\n")
                    .append("</ALLLEDGERENTRIES.LIST>\n")

                    .append("</VOUCHER>\n</TALLYMESSAGE>\n");

            counter++;
        }

        xml.append("</REQUESTDATA></IMPORTDATA></BODY></ENVELOPE>");
        return xml.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

}
