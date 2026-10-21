package com.capybee.server.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration
public class DatabaseConfig {

    @Bean
    @Primary
    public DataSource dataSource(
        @Value("${spring.datasource.url:jdbc:postgresql://localhost:5432/capybee}") String primaryUrl,
        @Value("${spring.datasource.username:capybee}") String username,
        @Value("${spring.datasource.password:capybee}") String password,
        @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}") String driverClassName
    ) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setJdbcUrl(DatabaseUrlNormalizer.normalize(primaryUrl));
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(5);
        dataSource.setMinimumIdle(0);
        dataSource.setMaxLifetime(900000);
        dataSource.setKeepaliveTime(120000);
        dataSource.setConnectionTimeout(30000);
        dataSource.setValidationTimeout(5000);
        return dataSource;
    }
}
