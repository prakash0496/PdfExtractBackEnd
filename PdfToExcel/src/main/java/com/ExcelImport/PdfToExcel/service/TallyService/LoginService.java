package com.ExcelImport.PdfToExcel.service.TallyService;

import com.ExcelImport.PdfToExcel.Entity.AdminLogin;
import com.ExcelImport.PdfToExcel.Entity.Login;
import com.ExcelImport.PdfToExcel.Repository.AdminRepository;
import com.ExcelImport.PdfToExcel.Repository.LoginRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import java.util.List;
import java.util.Optional;



@Service
@Log4j2
public class LoginService {

    @Autowired
    private LoginRepository loginRepository;

    @Autowired
    private AdminRepository adminRepository;

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

   public Optional<Login> usernamecheck(String username){
        Optional<Login> check = loginRepository.findByUsername(username);
        return check;
   }

    public String updatePassword(String username, String password) {

        Optional<Login> optionalLogin = loginRepository.findByUsername(username);

        if (optionalLogin.isPresent()) {

            Login login = optionalLogin.get();
            login.setPassword(password);
            loginRepository.save(login);

            return "Password updated successfully";
        }

        throw new RuntimeException("User not found");
    }


    public Optional<AdminLogin> adminLogin(String username, String password){

        Optional<AdminLogin> adminLoginOption = adminRepository.findByAdminnameAndPassword(username,password);

        return  adminLoginOption;
    }

    public List<Login> findAll() {
        return loginRepository.findAll();
    }

    public Login getById (Long id){
        return loginRepository.findById(id).orElseThrow(()-> new RuntimeException("User ID Not Found"));
    }

    public Login updateuser(Login login){
        log.info("ENTRY-EXIT - Update User Details");
        return  loginRepository.save(login);
    }

}
