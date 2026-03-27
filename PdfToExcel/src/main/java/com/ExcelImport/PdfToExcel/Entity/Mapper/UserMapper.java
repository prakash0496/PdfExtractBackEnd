package com.ExcelImport.PdfToExcel.Entity.Mapper;

import com.ExcelImport.PdfToExcel.Entity.Login;
import com.ExcelImport.PdfToExcel.dto.Request.UserRequestDTO;
import com.ExcelImport.PdfToExcel.dto.Response.UserResponse;
import com.ExcelImport.PdfToExcel.dto.Response.UserResponseDTO;
import lombok.extern.log4j.Log4j2;
import org.modelmapper.ModelMapper;
import org.modelmapper.convention.MatchingStrategies;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Log4j2
@Component
public class UserMapper {

    @Autowired
    private ModelMapper modelMapper;

    // List<Login> → List<UserResponse>
    public List<UserResponse> convertEntityToDTO(List<Login> loginList) {

        modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);

        return loginList.stream()
                .map(login -> modelMapper.map(login, UserResponse.class))
                .toList();
    }

    // Login → UserResponse
    public UserResponse convertEntityToDTOS(Login login) {

        modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);

        return modelMapper.map(login, UserResponse.class);
    }

    // UserRequestDTO → Login
    public void convertUpdateRequestToEntity(UserRequestDTO userRequestDTO, Login login) {

        modelMapper.getConfiguration().setMatchingStrategy(MatchingStrategies.STRICT);

        modelMapper.map(userRequestDTO, login);
    }

}