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

    public Optional<Login> login(String username, String password) {

        Optional<Login> logincheck = loginRepository.findByUsernameAndPassword(username, password);

        return logincheck;
    }
    // 🔑 Check serial number in database
    public boolean checkSerialNumber(String serialNumber) {
        return loginRepository.existsBySerialNumber(serialNumber);
    }

    public Login userRegister(Login login){
        log.info("ENTRY-EXIT - Save User Details ");
        return loginRepository.save(login);
    }

}
