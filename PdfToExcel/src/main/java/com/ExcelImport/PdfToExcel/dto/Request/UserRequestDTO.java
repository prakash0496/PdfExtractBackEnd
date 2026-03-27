package com.ExcelImport.PdfToExcel.dto.Request;


import lombok.*;
import org.springframework.stereotype.Component;

@Data
@Getter
@Setter
@NoArgsConstructor
@Component
public class UserRequestDTO {

    private String firstname;
    private String lastname;
    private String company;
    private String phonenumber;
    private String email_ids;
    private String serialNumber;

}
