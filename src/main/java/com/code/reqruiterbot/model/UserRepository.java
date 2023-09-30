package com.code.reqruiterbot.model;

import com.code.reqruiterbot.model.User;
import org.springframework.data.repository.CrudRepository;

public interface UserRepository extends CrudRepository<User, Long> {
}
