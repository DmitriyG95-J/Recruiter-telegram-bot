package com.code.reqruiterbot.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Configuration
@Data
@PropertySource("application.properties")
@ComponentScan("com.code.reqruiterbot")
public class BotConfig {
    @Value("${bot.name}")
    String botName;
    @Value("${bot.token}")
    String token;
    @Value("${bot.owner.1}")
    private Long owner1;
    @Value("${bot.owner.2}")
    private Long owner2;
    public List<Long> getBotOwners() {
        return Arrays.asList(owner1, owner2);
    }
}
