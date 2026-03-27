package com.ExcelImport.PdfToExcel.Entity;


import jakarta.persistence.*;
import lombok.*;

@Data
@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "admin_login")
public class AdminLogin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "admin_id")
    private Long adminid;

    @Column(name = "admin_name")
    private String adminname;

    @Column(name = "password")
    private String password;

}
