package com.code.reqruiterbot.model;

import com.code.reqruiterbot.model.BlackList;

import org.springframework.data.repository.CrudRepository;

import java.util.Optional;



public interface BlackListRepository extends CrudRepository<BlackList, Long> {
    Optional<BlackList> findByChatId(Long chatId);

}