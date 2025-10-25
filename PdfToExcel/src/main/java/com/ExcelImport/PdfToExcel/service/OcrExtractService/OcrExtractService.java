package com.ExcelImport.PdfToExcel.service.OcrExtractService;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

@Service
public class OcrExtractService {

    // Step 1: Run OCR and return extracted text
    public String extractTextFromScannedPdf(byte[] pdfBytes) throws Exception {
        try (PDDocument document = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);

            ITesseract tesseract = new Tesseract();
            tesseract.setDatapath("E:/PdfExtract/PdfToExcel/PdfToExcel/src/main/java/com/ExcelImport/PdfToExcel/tessdata");
            tesseract.setLanguage("eng");

            StringBuilder fullText = new StringBuilder();

            for (int page = 0; page < document.getNumberOfPages(); ++page) {
                BufferedImage image = pdfRenderer.renderImageWithDPI(page, 500);
                String extractedText = tesseract.doOCR(image);
                fullText.append(extractedText).append("\n");
            }

            return fullText.toString();
        }
    }

}
