package com.ExcelImport.PdfToExcel.dto.Response;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
public class UserResponse {

    private Long userid;
    private String firstname;
    private String lastname;
    private String company;
    private String phonenumber;
    private String email_ids;
    private String serialNumber;


}
