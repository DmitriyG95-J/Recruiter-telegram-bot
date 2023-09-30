package com.code.reqruiterbot.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Entity(name = "vacancydatatable")
@Data
public class Vacancy {
    private String jobTitle;
    private String ProjectDescription;
    private String Responsibilities;
    private String Requirements;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int vacancyId;
    public Long recruiterChatId;
}