package com.ExcelImport.PdfToExcel.Entity;


import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_login")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Login {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="user_id")
    private Long userId;

    @Column(name="first_name")
    private String firstname;

    @Column(name="last_name")
    private String lastname;

    @Column(name="company_name")
    private String company;

    @Column(name="phone_number")
    private String phonenumber;

    @Column(name="email_id")
    private String emailIds;

    @Column(name="user_name")
    private String username;

    @Column(name="pass_word")
    private String password;

    @Column(name = "serial_number", unique = true)
    private String serialNumber;
}
