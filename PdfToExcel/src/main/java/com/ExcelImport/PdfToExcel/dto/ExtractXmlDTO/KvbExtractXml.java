package com.ExcelImport.PdfToExcel.dto.ExtractXmlDTO;


import com.ExcelImport.PdfToExcel.dto.KvbTransactionDTO;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

import java.util.List;

@Data
@JacksonXmlRootElement(localName = "KvbTransactions")
public class KvbExtractXml {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "KvbTransaction")
    private List<KvbTransactionDTO> transactionDTOList;


}
