package com.ExcelImport.PdfToExcel.dto.ExtractXmlDTO;


import com.ExcelImport.PdfToExcel.dto.CanaraBankTransactionDTO;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement;
import lombok.Data;

import java.util.List;

@Data
@JacksonXmlRootElement(localName = "CanaraTransactions")
public class CanaraExtractXml {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "CanaraTransaction")
    private List<CanaraBankTransactionDTO> canaraBankTransactionDTOList;


}
