package com.ExcelImport.PdfToExcel.dto.Response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class UserResponseDTO {

    private Integer httpStatus;
    private Boolean success;
    private Date timestamp;
    private List<UserResponse> data;
    private ResponseError error;

    public UserResponseDTO(Integer httpStatus, Boolean success, List<UserResponse> data, ResponseError error) {
        this.httpStatus = httpStatus;
        this.success = success;
        this.data = data;
        this.error = error;
        this.timestamp = Calendar.getInstance().getTime();
    }
}