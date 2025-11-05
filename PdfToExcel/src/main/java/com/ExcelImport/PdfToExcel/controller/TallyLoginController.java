package com.ExcelImport.PdfToExcel.controller;


import com.ExcelImport.PdfToExcel.dto.LoginDTO;
import com.ExcelImport.PdfToExcel.service.TallyService.TallyVerificationService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

@CrossOrigin("*")
@RestController
@Log4j2
@RequestMapping("api/tally")
public class TallyLoginController {


//    @Autowired
//    private TallyVerificationService tallyVerificationService;
//
//    @PostMapping(value = "/verify")
//    public ResponseEntity<?> verifySerialAndAccount(@RequestBody LoginDTO loginDTO) {
//
//        try {
//            boolean isValid = tallyVerificationService.verifySerialAndAccount(loginDTO.getUsername(), loginDTO.getPassword());
//
//            if (isValid) {
//                // ✅ Redirect to main controller after success
//                RedirectView redirectView = new RedirectView("/api/pdf/extracts");
//                return ResponseEntity.ok(redirectView);
//            } else {
//                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
//                        .body("❌ Invalid Serial Number or Account ID");
//            }
//        } catch (Exception e) {
//            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
//                    .body("❌ Error verifying with Tally: " + e.getMessage());
//        }
//    }

}
