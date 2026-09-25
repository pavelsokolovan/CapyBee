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
        @Value("${SPRING_DATASOURCE_URL:${DATABASE_URL:jdbc:postgresql://localhost:5432/capybee}}") String primaryUrl,
        @Value("${SPRING_DATASOURCE_USERNAME:${DB_USERNAME:capybee}}") String username,
        @Value("${SPRING_DATASOURCE_PASSWORD:${DB_PASSWORD:capybee}}") String password
    ) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");
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
