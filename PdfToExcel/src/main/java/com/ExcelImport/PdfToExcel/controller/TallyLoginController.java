package com.ExcelImport.PdfToExcel.controller;


import com.ExcelImport.PdfToExcel.Entity.AdminLogin;
import com.ExcelImport.PdfToExcel.Entity.Login;
import com.ExcelImport.PdfToExcel.Entity.Mapper.UserMapper;
import com.ExcelImport.PdfToExcel.dto.Request.UserRequestDTO;
import com.ExcelImport.PdfToExcel.dto.Response.LoginResponse;
import com.ExcelImport.PdfToExcel.dto.Response.UserResponse;
import com.ExcelImport.PdfToExcel.dto.Response.UserResponseDTO;
import com.ExcelImport.PdfToExcel.service.TallyService.LoginService;
import lombok.extern.log4j.Log4j2;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


import java.util.List;
import java.util.Map;
import java.util.Optional;



 @CrossOrigin(origins = "https://pdfextractionfront.netlify.app",
       allowCredentials = "true")
/* @CrossOrigin(origins= "http://localhost:4200",
        allowCredentials = "true") */
@RestController
@Log4j2
@RequestMapping("/api/auth")
public class TallyLoginController {

    @Autowired
    private LoginService loginService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ModelMapper modelMapper;

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


    @PostMapping(value = "/adminlogin")
    public ResponseEntity<LoginResponse> adminlogin(@RequestParam String username,
                                                    @RequestParam String password) {
        Optional<AdminLogin> admin = loginService.adminLogin(username, password);

        if (admin.isPresent()){
            return ResponseEntity.ok( new LoginResponse(true,"Login Successful"));
        }
        return  ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new LoginResponse(false,"Invalid username or password"));
    }



    @PostMapping("/register")
    public Login registerUser(@RequestBody Login user) {
        log.info("User Details Stored");
        return loginService.userRegister(user);
    }

    @GetMapping("/check-user")
    public ResponseEntity<LoginResponse> checkUser(@RequestParam String username){
        Optional<Login> usernamedtls = loginService.usernamecheck(username);

        if (usernamedtls.isPresent()){
            return  ResponseEntity.ok(new LoginResponse(true,"Username is Present"));
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new LoginResponse(false,"Username Not Found"));
    }


    @PostMapping("/update-password")
    public String updatePassword(@RequestBody Map<String,String> data){

        String user = data.get("username");
        String pass = data.get("password");

        return loginService.updatePassword(user, pass);
    }

    @GetMapping("/fetchUserList")
    public ResponseEntity<UserResponseDTO> getAllUsers() {

        List<Login> loginList = loginService.findAll();

        List<UserResponse> userResponseList =
                userMapper.convertEntityToDTO(loginList);

        UserResponseDTO response =
                new UserResponseDTO(HttpStatus.OK.value(), true, userResponseList, null);

        return ResponseEntity.ok(response);
    }


    @PutMapping(value = "/{id}",produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserResponse> updateUserById(
            @RequestBody UserRequestDTO userRequestDTO,
            @PathVariable Long id) {

        Login login = loginService.getById(id);

        userMapper.convertUpdateRequestToEntity(userRequestDTO, login);

        login = loginService.updateuser(login);

        UserResponse userResponse = userMapper.convertEntityToDTOS(login);

        return ResponseEntity.ok(userResponse);
    }


    @GetMapping(value = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UserResponseDTO> fetchUserById(@PathVariable("id") Long id) {

        log.info("ENTRY - Fetch User by Id");

        Login login = loginService.getById(id);

        UserResponse userResponse = userMapper.convertEntityToDTOS(login);

        UserResponseDTO responseDto =
                new UserResponseDTO(HttpStatus.OK.value(), true, List.of(userResponse), null);

        log.info("EXIT");

        return ResponseEntity.ok(responseDto);
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