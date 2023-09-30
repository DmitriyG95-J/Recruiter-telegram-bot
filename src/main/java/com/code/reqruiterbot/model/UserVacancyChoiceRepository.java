package com.code.reqruiterbot.model;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserVacancyChoiceRepository extends JpaRepository<UserVacancyChoice, Long> {
    Long getUserChatIdByVacancyId(@Param("vacancyId") int vacancyId);
}
