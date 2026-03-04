package com.ExcelImport.PdfToExcel.service.TallyService;

import com.ExcelImport.PdfToExcel.Entity.Login;
import com.ExcelImport.PdfToExcel.Repository.LoginRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Log4j2
public class LoginService {

    @Autowired
    private LoginRepository loginRepository;

    public  Optional<Login> login (Login request) {

        return loginRepository.findByUsernameAndPassword(
                request.getUsername().trim(),
                request.getPassword().trim()
        );
    }

    public Login userRegister(Login login){
        log.info("ENTRY-EXIT - Save User Details ");
        return loginRepository.save(login);
    }

}
