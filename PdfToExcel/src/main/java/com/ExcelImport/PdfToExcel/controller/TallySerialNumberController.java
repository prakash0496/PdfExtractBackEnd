package com.ExcelImport.PdfToExcel.controller;

import org.springframework.web.bind.annotation.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@CrossOrigin(origins = "http://localhost:4200", allowCredentials = "true")
@RestController
@RequestMapping("/tally")
public class TallySerialNumberController {

    @GetMapping("/serial-number")
    public String getTallyLicenseSerialNumber() {

        // Correct Tally request format for license information
        String xmlRequest =
                "<ENVELOPE>" +
                        " <HEADER>" +
                        "  <VERSION>1</VERSION>" +
                        "  <TALLYREQUEST>Export</TALLYREQUEST>" +
                        "  <TYPE>Collection</TYPE>" +
                        "  <ID>License Info</ID>" +
                        " </HEADER>" +
                        " <BODY>" +
                        "  <DESC>" +
                        "   <STATICVARIABLES>" +
                        "    <SVEXPORTFORMAT>$$SysName:XML</SVEXPORTFORMAT>" +
                        "   </STATICVARIABLES>" +
                        "  </DESC>" +
                        " </BODY>" +
                        "</ENVELOPE>";

        try {
            System.out.println("Sending request to Tally...");

            URL url = new URL("http://localhost:9000");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "text/xml");
            conn.setRequestProperty("Accept", "text/xml");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            // Send request
            try (OutputStream os = conn.getOutputStream()) {
                os.write(xmlRequest.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            // Check response code
            int responseCode = conn.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            if (responseCode != 200) {
                return "Error: Tally returned response code " + responseCode;
            }

            // Read response
            StringBuilder response = new StringBuilder();
            try (Scanner scanner = new Scanner(conn.getInputStream(), StandardCharsets.UTF_8.name())) {
                while (scanner.hasNextLine()) {
                    response.append(scanner.nextLine());
                }
            }

            String tallyResponse = response.toString();
            System.out.println("Tally Response: " + tallyResponse);

            // Parse the response
            return extractLicenseInfo(tallyResponse);

        } catch (java.net.ConnectException e) {
            return "Error: Cannot connect to Tally on localhost:9000. Make sure Tally is running with HTTP server enabled";
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    private String extractLicenseInfo(String xmlResponse) {
        if (xmlResponse == null || xmlResponse.trim().isEmpty()) {
            return "Empty response from Tally";
        }

        try {
            // Parse the XML response
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xmlResponse)));
            document.getDocumentElement().normalize();

            // Look for LICENSEINFO or similar tags
            NodeList licenseInfoNodes = document.getElementsByTagName("LICENSEINFO");
            if (licenseInfoNodes.getLength() == 0) {
                licenseInfoNodes = document.getElementsByTagName("LicenseInfo");
            }
            if (licenseInfoNodes.getLength() == 0) {
                licenseInfoNodes = document.getElementsByTagName("LICENSE");
            }

            if (licenseInfoNodes.getLength() > 0) {
                Element licenseElement = (Element) licenseInfoNodes.item(0);

                // Look for serial number within license info
                NodeList serialNodes = licenseElement.getElementsByTagName("SERIALNUMBER");
                if (serialNodes.getLength() == 0) {
                    serialNodes = licenseElement.getElementsByTagName("SerialNumber");
                }
                if (serialNodes.getLength() == 0) {
                    serialNodes = licenseElement.getElementsByTagName("SERIAL");
                }

                if (serialNodes.getLength() > 0) {
                    return serialNodes.item(0).getTextContent().trim();
                }

                // If no serial tag, return the entire license info for debugging
                return "License info found but no serial number: " + licenseElement.getTextContent().trim();
            }

            // If still not found, try a more general search
            NodeList allElements = document.getElementsByTagName("*");
            for (int i = 0; i < allElements.getLength(); i++) {
                Element element = (Element) allElements.item(i);
                String tagName = element.getTagName().toLowerCase();
                String textContent = element.getTextContent().trim();

                if ((tagName.contains("serial") || tagName.contains("license")) &&
                        !textContent.isEmpty() &&
                        textContent.length() >= 5) {
                    return textContent;
                }
            }

            return "License information not found in response";

        } catch (Exception e) {
            return "Error parsing XML: " + e.getMessage();
        }
    }
}