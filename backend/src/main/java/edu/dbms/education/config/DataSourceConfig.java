package edu.dbms.education.config;

import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * Dual-datasource configuration.
 *
 * DBMS concept: Least-privilege principle.
 *   flyway_user  – has DDL + GRANT rights; used only at startup by Flyway
 *   app_user     – runtime DML / CALL only; no DDL access
 *
 * Spring Boot auto-configuration for DataSource is disabled by the
 * @Primary annotation here overriding the auto-configured one.
 * Flyway auto-config is disabled in application.yml (spring.flyway.enabled=false).
 */
@Configuration
public class DataSourceConfig {

    // ── 1. Flyway datasource (flyway_user) ────────────────────────────────

    /**
     * Reads spring.flyway-datasource.* from application.yml.
     * Only injected into the Flyway bean below; the rest of the app never sees it.
     */
    @Bean("flywayDataSource")
    @ConfigurationProperties(prefix = "spring.flyway-datasource")
    public HikariDataSource flywayDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    /**
     * Flyway bean wired to flyway_user.
     * Migrations live in classpath:db/migration (default location).
     * outOfOrder = false, validateOnMigrate = true (safe defaults).
     */
    @Bean("flyway")
    public Flyway flyway(@Qualifier("flywayDataSource") DataSource flywayDataSource) {
        return Flyway.configure()
                .dataSource(flywayDataSource)
                .locations("classpath:db/migration")
                .validateOnMigrate(true)
                .outOfOrder(false)
                .load();
    }

    /**
     * Triggers Flyway migration on startup, before any app-user beans try
     * to access tables that may not yet exist.
     */
    @Bean
    public FlywayMigrationInitializer flywayInitializer(@Qualifier("flyway") Flyway flyway) {
        return new FlywayMigrationInitializer(flyway);
    }

    // ── 2. Application datasource (app_user) ─────────────────────────────

    /**
     * Primary datasource – used by all JdbcTemplate / SimpleJdbcCall beans.
     * Must wait for Flyway to finish before the schema exists.
     */
    @Primary
    @Bean("appDataSource")
    @DependsOn("flywayInitializer")
    @ConfigurationProperties(prefix = "spring.datasource")
    public HikariDataSource appDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    // ── 3. JdbcTemplate wired to appDataSource ────────────────────────────
    // NOTE: TracingJdbcTemplate extends JdbcTemplate and is registered as a
    //       @Component.  It is the PRIMARY JdbcTemplate bean used everywhere.
    //       We do NOT declare a second JdbcTemplate bean here to avoid the
    //       duplicate-primary conflict; TracingJdbcTemplate satisfies all
    //       JdbcTemplate injection points.

    /**
     * NamedParameterJdbcTemplate wraps the TracingJdbcTemplate.
     * Spring injects the TracingJdbcTemplate (the single JdbcTemplate bean)
     * via the JdbcTemplate parameter type.
     *
     * Useful for queries with many named parameters (:paramName syntax).
     */
    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(JdbcTemplate jdbcTemplate) {
        return new NamedParameterJdbcTemplate(jdbcTemplate);
    }
}
