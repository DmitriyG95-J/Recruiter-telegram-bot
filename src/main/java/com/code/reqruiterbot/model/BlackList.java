package com.code.reqruiterbot.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Data;

import java.sql.Timestamp;

@Entity(name = "userBlackList")
@Data
public class BlackList {
    @Id
    private Long chatId;
    private String firstName;
    private String userName;
    private Timestamp bannedAt;
}
