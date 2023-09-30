package com.code.reqruiterbot.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;


import java.sql.Timestamp;

@Data
@Entity(name = "userDataTable")
public class User {
    @Id
    private Long chatId;
    private String firstName;
    private String lastName;
    private String userName;
    private Timestamp registeredAt;
}




