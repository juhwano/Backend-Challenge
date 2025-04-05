package com.antock.backend.config;

import com.antock.backend.repository.BusinessEntityStorage;
import com.antock.backend.repository.JpaBusinessEntityStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class StorageConfig {
    
    @Bean
    @Primary
    public BusinessEntityStorage businessEntityStorage(JpaBusinessEntityStorage jpaStorage) {
        // 현재는 JPA 구현체를 사용하지만, 향후 다른 구현체로 쉽게 교체 가능
        return jpaStorage;
    }
}