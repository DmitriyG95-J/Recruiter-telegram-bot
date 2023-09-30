package com.code.reqruiterbot.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity(name = "uservacancychoice")
@Data
public class UserVacancyChoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId; // ID пользователя
    private Integer vacancyId; // ID вакансии
    private Long recruiterChatId; // ID чата рекрутера
}
