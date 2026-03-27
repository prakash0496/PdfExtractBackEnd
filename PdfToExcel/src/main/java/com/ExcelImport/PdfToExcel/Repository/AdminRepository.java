package com.ExcelImport.PdfToExcel.Repository;

import com.ExcelImport.PdfToExcel.Entity.AdminLogin;
import com.ExcelImport.PdfToExcel.Entity.Login;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminRepository extends JpaRepository<AdminLogin , Long> {

    Optional<AdminLogin> findByAdminnameAndPassword(String adminname, String password);

}
