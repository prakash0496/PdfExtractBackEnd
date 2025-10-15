
                      /* KVB  Statement DTO */
                   /*==========================*/

package com.ExcelImport.PdfToExcel.dto;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.*;

import java.util.List;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JacksonXmlRootElement(localName = "KvbTransaction")
public class KvbTransactionDTO {
    @JacksonXmlProperty(localName = "TransactionDate")
    private String transactionDate;
    @JacksonXmlProperty(localName = "ValueDate")
    private String valueDate;
    @JacksonXmlProperty(localName = "Branch")
    private String branch;
    @JacksonXmlProperty(localName = "ChequeNo")
    private String chequeNo;
    @JacksonXmlProperty(localName = "Description")
    private String description;
    @JacksonXmlProperty(localName = "Debit")
    private String debit;
    @JacksonXmlProperty(localName = "Credit")
    private String credit;
    @JacksonXmlProperty(localName = "Balance")
    private String balance;
    private String voucherName;
    private String ledgerName;
    private List<String> amounts; // + getter and setter

//    public void setAmounts(List<String> amounts) {
//        this.amounts = amounts;
//    }
//



//
//    public KvbTransactionDTO() {
//
//    }

//    public KvbTransactionDTO(String transactionDate, String valueDate, String branch, String chequeNo, String description, String debit, String credit, String balance) {
//        this.transactionDate = transactionDate;
//        this.valueDate = valueDate;
//        this.branch = branch;
//        this.chequeNo = chequeNo;
//        this.description = description;
//        this.debit = debit;
//        this.credit = credit;
//        this.balance = balance;
//    }
//
//    public void setTransactionDate(String transactionDate) {
//        this.transactionDate = transactionDate;
//    }
//
//    public void setValueDate(String valueDate) {
//        this.valueDate = valueDate;
//    }
//
//    public void setBranch(String branch) {
//        this.branch = branch;
//    }
//
//    public void setChequeNo(String chequeNo) {
//        this.chequeNo = chequeNo;
//    }
//
//    public void setDescription(String description) {
//        this.description = description;
//    }
//
//    public void setDebit(String debit) {
//        this.debit = debit;
//    }
//
//    public void setCredit(String credit) {
//        this.credit = credit;
//    }
//
//    public void setBalance(String balance) {
//        this.balance = balance;
//    }

}