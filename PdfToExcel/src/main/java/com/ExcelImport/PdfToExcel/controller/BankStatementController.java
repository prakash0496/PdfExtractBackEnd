package com.ExcelImport.PdfToExcel.controller;

import com.ExcelImport.PdfToExcel.dto.*;
import com.ExcelImport.PdfToExcel.dto.Response.BankResponse;
import com.ExcelImport.PdfToExcel.dto.Response.TransactionDTO;
import com.ExcelImport.PdfToExcel.dto.Response.TransactionResponseDTO;
import com.ExcelImport.PdfToExcel.service.ExcelService.CanaraBankStatementExcelService;
import com.ExcelImport.PdfToExcel.service.ExcelService.KvbBankStatementExcelService;
import com.ExcelImport.PdfToExcel.service.ExtractService.*;
import com.ExcelImport.PdfToExcel.service.OcrExtractService.BankStatementParser;
import com.ExcelImport.PdfToExcel.service.OcrExtractService.OcrExtractService;
import com.ExcelImport.PdfToExcel.service.OcrExtractService.PdfGeneratorService;
import com.ExcelImport.PdfToExcel.service.TallyService.TallyConversionService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@CrossOrigin("*")
@RestController
@Log4j2
@RequestMapping("/api/pdf")
public class BankStatementController {

    private final KvbBankStatementService kvbBankStatementService;
    private final KvbBankStatementExcelService kvbBankStatementExcelService;
    private final CanaraBankStatementService canaraBankStatementService;
    private final CanaraBankStatementExcelService canaraBankStatementExcelService;
    private final TallyConversionService tallyConversionService;
    private final FederalBankStatementService federalBankStatementService;
    private final HdfcBankStatementService hdfcBankStatementService;
    private final ICICIBankStatementService iciciBankStatementService;
    private final StateBankStatementService stateBankStatementService;
    private final InduslndBankStatementService induslndBankStatementService;
    private final CityUnionBankStatementService cityUnionBankStatementService;
    private  final IndianBankStatementService indianBankStatementService;
    private final UniverselExtractorService extractor;

    @Autowired
    public BankStatementController(KvbBankStatementService kvbBankStatementService, KvbBankStatementExcelService kvbBankStatementExcelService,
                                   CanaraBankStatementService canaraBankStatementService, CanaraBankStatementExcelService canaraBankStatementExcelService ,
                                   FederalBankStatementService federalBankStatementService, ICICIBankStatementService iciciBankStatementService,
                                   HdfcBankStatementService hdfcBankStatementService, StateBankStatementService stateBankStatementService,
                                   CityUnionBankStatementService cityUnionBankStatementService,InduslndBankStatementService induslndBankStatementService,
                                   IndianBankStatementService indianBankStatementService,OcrExtractService ocrExtractService,TallyConversionService tallyConversionService,
                                   UniverselExtractorService extractor) {
        this.kvbBankStatementService = kvbBankStatementService;
        this.canaraBankStatementService = canaraBankStatementService;
        this.kvbBankStatementExcelService = kvbBankStatementExcelService;
        this.canaraBankStatementExcelService = canaraBankStatementExcelService; // ✅ assign properly
        this.federalBankStatementService = federalBankStatementService;
        this.iciciBankStatementService = iciciBankStatementService;
        this.hdfcBankStatementService = hdfcBankStatementService;
        this.induslndBankStatementService = induslndBankStatementService;
        this.stateBankStatementService = stateBankStatementService;
        this.cityUnionBankStatementService = cityUnionBankStatementService;
        this.indianBankStatementService = indianBankStatementService;
        this.extractor = extractor;
        this.tallyConversionService = tallyConversionService;
    }

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<?> extractTransactions(@RequestParam("file") MultipartFile file, @RequestParam("bank") String bank,@RequestParam(value = "password",required = false) String password) throws Exception {
        List<?> transactions;
        switch (bank.toUpperCase()) {
            case "KVB": // OCR-based extraction
                String ocrText = kvbBankStatementService.extractTextFromScannedPdf(file.getBytes());
                log.info("🔎 OCR Extracted Text (KVB):\n" + ocrText);
                transactions = kvbBankStatementService.extractTransactions(ocrText);
                break;

            case "CANARA": // Tabula table extraction
                List<List<String>> tableRows = canaraBankStatementService.extractTableFromPdf(file.getBytes());
                log.info("🔎 Table Rows Extracted (IndianBank):\n" + tableRows);
                transactions = canaraBankStatementService.mapTableToDto(tableRows);
                break;

            case "INDIAN_BANK": // Tabula table extraction
                transactions = indianBankStatementService.extractTransactionsAsDTO(file.getBytes());
                break;


            case "FEDERAL":
                List<List<String>> federalTableRows = federalBankStatementService.extractTableFromPdf(file.getBytes());
                log.info("🔎 Table Rows Extracted (Federal):\n" +federalTableRows);
                transactions = federalBankStatementService.mapFederalTableToDto(federalTableRows);
                break;

            case "ICICI":
                // FIXED: Extract text first, then parse transactions
                String pdfText = iciciBankStatementService.extractTextFromPdf(file.getBytes());
                log.info("🔎 Extracted Text (ICICI):\n" + pdfText);
                transactions = iciciBankStatementService.extractTransaction(pdfText);
                break;

            case "HDFC":
                // FIXED: Extract text first, then parse transactions
                String hdfcpdfText = hdfcBankStatementService.extractTextFromPdf(file.getBytes(),password);
                log.info("🔎 Extracted Text (Hdfc):\n" + hdfcpdfText);
                transactions = hdfcBankStatementService.extractHdfcTransaction(hdfcpdfText);
                break;

            case "INDUSLND":
                transactions = induslndBankStatementService.extractTransactions(file.getBytes());
                log.info("🔎 Table Rows Extracted (Induslnd):\n" + transactions);
//                transactions = induslndBankStatementService.(tableindusRows);
                break;

            case "SBI": // Tabula table extraction
                List<List<String>> sbitableRows = stateBankStatementService.extractTableFromPdf(file.getBytes());
                log.info("🔎 Table Rows Extracted (SBI):\n" + sbitableRows);
                transactions = stateBankStatementService.mapTableToDto(sbitableRows);
                break;

            case "CITY_UNION": // Tabula table extraction
                List<List<String>> citytableRows = cityUnionBankStatementService.extractTableFromPdf(file.getBytes());
                log.info("🔎 Table Rows Extracted (CITY_UNION_BANK):\n" + citytableRows);
                transactions = cityUnionBankStatementService.mapTableToDto(citytableRows);
                break;

            default:
                throw new IllegalArgumentException("Unsupported bank: " + bank);
        }

        log.info("✅ Extracted " + transactions.size() + " transactions for " + bank);
        return transactions;
    }


    @PostMapping(value = "/download-excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> downloadExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("bank") String bank) {

        try {
            byte[] excelBytes;

            switch (bank.toUpperCase()) {
                case "KVB":
                    String kvbText = kvbBankStatementService.extractTextFromScannedPdf(file.getBytes());
                    List<KvbTransactionDTO> kvbTransactions = kvbBankStatementService.extractTransactions(kvbText);
                    excelBytes = kvbBankStatementExcelService.generateExcel(kvbTransactions);
                    break;

                case "CANARA":
                    List<List<String>> ocrTexts = canaraBankStatementService.extractTableFromPdf(file.getBytes());
                    List<CanaraBankTransactionDTO> canaraTransactions = canaraBankStatementService.mapTableToDto(ocrTexts);
                    excelBytes = canaraBankStatementExcelService.generateExcel(canaraTransactions);
                    break;

                default:
                    throw new IllegalArgumentException("Unsupported bank: " + bank);
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + bank + "_BankStatement.xlsx")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(excelBytes);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }


    @PostMapping(value = "/extract-json", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BankResponse> extractTransactionsAsJson(
            @RequestParam("file") MultipartFile file,
            @RequestParam("bank") String bank) throws Exception {

        List<TransactionResponseDTO> transactions = new ArrayList<>();

        switch (bank.toUpperCase()) {

            case "KVB":
                String ocrText = kvbBankStatementService.extractTextFromScannedPdf(file.getBytes());
                List<KvbTransactionDTO> kvbTransactions = kvbBankStatementService.extractTransactions(ocrText);

                List<TransactionResponseDTO> jsonTransactions = kvbTransactions.stream()
                        .map(tx -> new TransactionResponseDTO(
                                tx.getTransactionDate(),
                                tx.getValueDate(),
                                tx.getChequeNo(),
                                tx.getBranch(),
                                tx.getDescription(),
                                tx.getDebit(),
                                tx.getCredit(),
                                tx.getBalance(),
                                tx.getVoucherName(),
                                tx.getLedgerName()
                        ))
                        .toList();

                transactions.addAll(jsonTransactions);

                break;

            case "CANARA":
                // Extract table from PDF
                List<List<String>> tableRows = canaraBankStatementService.extractTableFromPdf(file.getBytes());
                List<CanaraBankTransactionDTO> dtos = canaraBankStatementService.mapTableToDto(tableRows);

                // Convert each DTO to TransactionResponseDTO
                dtos.forEach(tx -> transactions.add(new TransactionResponseDTO(
                        tx.getTransactionDate(),
                        tx.getValueDate(),
                        tx.getChequeNo(),
                        tx.getBranchCode(),
                        tx.getDescription(),
                        tx.getDebit(),
                        tx.getCredit(),
                        tx.getBalance(),
                        tx.getVoucherName(),
                        tx.getLedgerName()

                )));
                break;

            case "INDIAN_BANK":
                List<IndianBankTransactionDTO> dtoList = indianBankStatementService.extractTransactionsAsDTO(file.getBytes());

                dtoList.forEach(tx -> transactions.add(new TransactionResponseDTO(
                        tx.getTransactionDate(),
                        tx.getValueDate(),
                        tx.getChequeNo(),
                        tx.getBranchCode(),
                        tx.getDescription(),
                        tx.getDebit(),
                        tx.getCredit(),
                        tx.getBalance(),
                        tx.getVoucherName(),
                        tx.getLedgerName()
                )));
                break;



            case "FEDERAL":
                // Extract table from PDF
                List<List<String>> federalTable = federalBankStatementService.extractTableFromPdf(file.getBytes());
                List<FederalBankTransactionDTO> fedaral = federalBankStatementService.mapFederalTableToDto(federalTable);

                // Convert each DTO to TransactionResponseDTO
                fedaral.forEach(tx -> transactions.add(new TransactionResponseDTO(
                        tx.getTransactionDate(),
                        tx.getValueDate(),
                        tx.getChequeNo(),
                        tx.getBranch(),
                        tx.getDescription(),
                        tx.getDebit(),
                        tx.getCredit(),
                        tx.getBalance(),
                        tx.getVoucherName(),
                        tx.getLedgerName()
                )));
                break;

            case "INDUSLND":
                // Extract table from PDF
                List<InduslndBankTransactionDTO> induslndTable = induslndBankStatementService.extractTransactions(file.getBytes());

                // Convert each DTO to TransactionResponseDTO
                induslndTable.forEach(tx -> transactions.add(new TransactionResponseDTO(
                        tx.getTransactionDate(),
                        tx.getValueDate(),
                        tx.getChequeNo(),
                        tx.getBranchCode(),
                        tx.getDescription(),
                        tx.getDebit(),
                        tx.getCredit(),
                        tx.getBalance(),
                        tx.getVoucherName(),
                        tx.getLedgerName()
                )));
                break;

            case "ICICI":
                // Extract table from PDF
                String iciciTable = iciciBankStatementService.extractTextFromPdf(file.getBytes());
                List<ICICIBankTransactionDTO> iciciBankTransactionDTOS = iciciBankStatementService.extractTransaction(iciciTable);

                // Convert each DTO to TransactionResponseDTO
                iciciBankTransactionDTOS.forEach(tx -> transactions.add(new TransactionResponseDTO(
                        tx.getTransactionDate(),
                        tx.getValueDate(),
                        tx.getChequeNo(),
                        tx.getBranchCode(),
                        tx.getDescription(),
                        tx.getDebit(),
                        tx.getCredit(),
                        tx.getBalance(),
                        tx.getVoucherName(),
                        tx.getLedgerName()
                )));
                break;

            case "SBI":
                // Extract table from PDF
                List<List<String>> sbiTableRows = stateBankStatementService.extractTableFromPdf(file.getBytes());
                List<StateBankTransactionDTO> stateBankTransactionDTOS = stateBankStatementService.mapTableToDto(sbiTableRows);

                // Convert each DTO to TransactionResponseDTO
                stateBankTransactionDTOS.forEach(tx -> transactions.add(new TransactionResponseDTO(
                        tx.getTransactionDate(),
                        tx.getValueDate(),
                        tx.getChequeNo(),
                        tx.getBranchCode(),
                        tx.getDescription(),
                        tx.getDebit(),
                        tx.getCredit(),
                        tx.getBalance(),
                        tx.getVoucherName(),
                        tx.getLedgerName()
                )));
                break;

            case "CITY_UNION":
                // Extract table from PDF
                List<List<String>> cubTableRows = cityUnionBankStatementService.extractTableFromPdf(file.getBytes());
                List<CityUnionBankTransactionDTO> cityUnionBankTransactionDTOS = cityUnionBankStatementService.mapTableToDto(cubTableRows);

                // Convert each DTO to TransactionResponseDTO
                cityUnionBankTransactionDTOS.forEach(tx -> transactions.add(new TransactionResponseDTO(
                        tx.getTransactionDate(),
                        tx.getValueDate(),
                        tx.getChequeNo(),
                        tx.getBranchCode(),
                        tx.getDescription(),
                        tx.getDebit(),
                        tx.getCredit(),
                        tx.getBalance(),
                        tx.getVoucherName(),
                        tx.getLedgerName()
                )));
                break;


            default:
                throw new IllegalArgumentException("Unsupported bank: " + bank);
        }

        // Wrap in BankResponse DTO
        BankResponse response = new BankResponse("success", bank, transactions);

        return ResponseEntity.ok(response); // Spring Boot automatically converts DTO to JSON
    }



    @PostMapping(value = "/extract/tallyxml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<byte[]> extractTallyXml(
            @RequestParam("bank") String bank,
            @RequestParam("tableData") String tableDataJson,
            @RequestParam(value ="bankName",required = false) String typeBank) {

        try {
            ObjectMapper mapper = new ObjectMapper();
            byte[] tallyXml;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_XML);

            switch (bank.toUpperCase()) {
                case "CANARA":
                    List<CanaraBankTransactionDTO> canaraTransactions =
                            mapper.readValue(tableDataJson, new TypeReference<List<CanaraBankTransactionDTO>>() {});
                    tallyXml = tallyConversionService.generateTallyXml(tableDataJson, bank,typeBank);
                    headers.setContentDisposition(ContentDisposition.attachment()
                            .filename("TallyImport_Canara.xml").build());
                    break;

                case "KVB":
                    List<KvbTransactionDTO> kvbTransactions =
                            mapper.readValue(tableDataJson, new TypeReference<List<KvbTransactionDTO>>() {});
                    tallyXml = tallyConversionService.generateTallyXml(tableDataJson, bank,typeBank);
                    headers.setContentDisposition(ContentDisposition.attachment()
                            .filename("TallyImport_KVB.xml").build());
                    break;

                case "FEDERAL":
                    List<FederalBankTransactionDTO> federalBankTransactionDTOS =
                            mapper.readValue(tableDataJson, new TypeReference<List<FederalBankTransactionDTO>>() {});
                    tallyXml = tallyConversionService.generateTallyXml(tableDataJson, bank,typeBank);
                    headers.setContentDisposition(ContentDisposition.attachment()
                            .filename("TallyImport_Fedaral.xml").build());
                    break;
                case "ICICI":
                    List<ICICIBankTransactionDTO> iciciBankTransactionDTOS  =
                            mapper.readValue(tableDataJson, new TypeReference<List<ICICIBankTransactionDTO>>() {});
                    tallyXml = tallyConversionService.generateTallyXml(tableDataJson, bank,typeBank);
                    headers.setContentDisposition(ContentDisposition.attachment()
                            .filename("TallyImport_ICICI.xml").build());
                    break;

                case "SBI":
                    List<StateBankTransactionDTO>  stateBankTransactionDTOS =
                            mapper.readValue(tableDataJson, new TypeReference<List<StateBankTransactionDTO>>() {});
                    tallyXml = tallyConversionService.generateTallyXml(tableDataJson, bank,typeBank);
                    headers.setContentDisposition(ContentDisposition.attachment()
                            .filename("TallyImport_SBI.xml").build());
                    break;

                case "INDUSLND":
                    List<InduslndBankTransactionDTO> induslndBankTransactionDTOS  =
                            mapper.readValue(tableDataJson, new TypeReference<List<InduslndBankTransactionDTO>>() {});
                    tallyXml = tallyConversionService.generateTallyXml(tableDataJson, bank,typeBank);
                    headers.setContentDisposition(ContentDisposition.attachment()
                            .filename("TallyImport_INDUSLND.xml").build());
                    break;

                default:
                    throw new IllegalArgumentException("Unsupported bank: " + bank);
            }

            return new ResponseEntity<>(tallyXml, headers, HttpStatus.OK);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("<error>" + e.getMessage() + "</error>").getBytes());
        }
    }








//    @PostMapping(value = "/extract/tallyxml", produces = MediaType.APPLICATION_XML_VALUE)
//    public ResponseEntity<byte[]> extractTallyXml(
//            @RequestParam("file") MultipartFile file,
//            @RequestParam("bank") String bank) {
//
//        try {
//            byte[] fileBytes = file.getBytes();
//            byte[] tallyXml;
//            HttpHeaders headers = new HttpHeaders();
//            headers.setContentType(MediaType.APPLICATION_XML);
//
//            switch (bank.toUpperCase()) {
//
//                case "CANARA":
//                    // 🏦 1. Extract data
//                    List<List<String>> canaraOcrText = canaraBankStatementService.extractTableFromPdf(fileBytes);
//                    List<CanaraBankTransactionDTO> canaraTransactions =
//                            canaraBankStatementService.mapTableToDto(canaraOcrText);
//
//                    // 🧾 2. Convert to Tally XML
//                    tallyXml = tallyConversionService.convertToTallyXmlGeneric(canaraTransactions,bank);
//                    headers.setContentDisposition(ContentDisposition.attachment()
//                            .filename("TallyImport_Canara.xml").build());
//                    break;
//
//                case "KVB":
//                    String kvbText = kvbBankStatementService.extractTextFromScannedPdf(fileBytes);
//                    List<KvbTransactionDTO> kvbTransactions = kvbBankStatementService.extractTransactions(kvbText);
//
//                    tallyXml = tallyConversionService.convertToTallyXmlGeneric(kvbTransactions,bank);
//                    headers.setContentDisposition(ContentDisposition.attachment()
//                            .filename("TallyImport_KVB.xml").build());
//                    break;
//
//                default:
//                    throw new IllegalArgumentException("Unsupported bank: " + bank);
//            }
//
//            return new ResponseEntity<>(tallyXml, headers, HttpStatus.OK);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body(("<error>" + e.getMessage() + "</error>").getBytes());
//        }
//    }

    @PostMapping("/extracts")
    public ResponseEntity<List<Map<String, String>>> extractPdf(@RequestParam("file") MultipartFile file) throws Exception {
        List<Map<String, String>> transactions = extractor.extractAndParsePdf(file.getBytes());
        return ResponseEntity.ok(transactions);
    }


}


