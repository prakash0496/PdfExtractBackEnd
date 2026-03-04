package com.ExcelImport.PdfToExcel.controller;


import com.ExcelImport.PdfToExcel.Entity.Login;
import com.ExcelImport.PdfToExcel.dto.LoginDTO;
import com.ExcelImport.PdfToExcel.dto.Response.LoginResponse;
import com.ExcelImport.PdfToExcel.service.TallyService.LoginService;
import com.ExcelImport.PdfToExcel.service.TallyService.TallyVerificationService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Optional;

@CrossOrigin("*")
@RestController
@Log4j2
@RequestMapping("api/auth")
public class TallyLoginController {

    @Autowired
    private LoginService loginService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody Login request) {
        Optional<Login> user = loginService.login(request);

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
}