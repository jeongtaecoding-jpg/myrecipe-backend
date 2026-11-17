package com.myrecipe.global.config;

import com.myrecipe.global.security.JwtConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

// JwtConfig(jwt.* 설정)를 스프링 빈으로 등록한다.
@Configuration
@EnableConfigurationProperties(JwtConfig.class)
public class JwtPropertiesConfig {
}
