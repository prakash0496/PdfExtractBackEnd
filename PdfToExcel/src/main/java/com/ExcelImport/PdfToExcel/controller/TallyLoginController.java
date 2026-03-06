package com.ExcelImport.PdfToExcel.controller;


import com.ExcelImport.PdfToExcel.Entity.Login;
import com.ExcelImport.PdfToExcel.dto.LoginDTO;
import com.ExcelImport.PdfToExcel.dto.Request.LoginRequest;
import com.ExcelImport.PdfToExcel.dto.Response.LoginResponse;
import com.ExcelImport.PdfToExcel.service.TallyService.LoginService;
import com.ExcelImport.PdfToExcel.service.TallyService.TallyVerificationService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@CrossOrigin(origins = "https://pdfextractionfront.netlify.app",
       allowCredentials = "true")
/*@CrossOrigin(origins= "http://localhost:4200",
        allowCredentials = "true") */
@RestController
@Log4j2
@RequestMapping("/api/auth")
public class TallyLoginController {

    @Autowired
    private LoginService loginService;

    @PostMapping(value = "/login", consumes = "multipart/form-data")
    public ResponseEntity<LoginResponse> login(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam("file") MultipartFile file) throws Exception {

        Optional<Login> user = loginService.login(username, password);

        // Read license file
        String content = new String(file.getBytes());
        String serialNumber = extractSerialNumber(content);
        log.info("serial number " + serialNumber);

        if (serialNumber == null || serialNumber.trim().isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(false, "License number not found"));
        }

        boolean serialExists = loginService.checkSerialNumber(serialNumber);

        if (!serialExists) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new LoginResponse(false, "Invalid License"));
        }

        if (user.isPresent()) {
            return ResponseEntity.ok(
                    new LoginResponse(true, "Login successful")
            );
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new LoginResponse(false, "Invalid username or password"));
    }


    @PostMapping("/register")
    public Login registerUser(@RequestBody Login user) {
        log.info("User Details Stored");
        return loginService.userRegister(user);
    }


    private String extractSerialNumber(String content) {

        if (content == null) {
            return null;
        }

        // Remove everything except digits
        String digitsOnly = content.replaceAll("[^0-9]", "");

        // Check if at least 9 digits exist
        if (digitsOnly.length() >= 9) {
            return digitsOnly.substring(0, 9);
        }

        return null;
    }

}