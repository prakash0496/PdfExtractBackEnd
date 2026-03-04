package com.ExcelImport.PdfToExcel.dto.Response;

import lombok.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Data
@NoArgsConstructor
public class LoginResponse {

    private boolean success;
    private String message;

    public LoginResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getMessage() {
        return message;
    }
}