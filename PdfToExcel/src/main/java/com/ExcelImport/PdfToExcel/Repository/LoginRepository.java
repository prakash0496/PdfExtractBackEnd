package com.ExcelImport.PdfToExcel.Repository;

import com.ExcelImport.PdfToExcel.Entity.Login;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoginRepository extends JpaRepository<Login, Long>{

    Optional<Login> findByUsernameAndPassword(String username, String password);

    boolean existsBySerialNumber(String serialNumber);

    Optional<Login>findByUsername(String username);

   // Login findByUsername(String username);
}
