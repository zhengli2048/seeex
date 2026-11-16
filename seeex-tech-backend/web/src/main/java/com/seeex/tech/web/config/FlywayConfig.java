package com.seeex.tech.web.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring Boot 4 下显式执行 web 模块的 Flyway 迁移。 */
@Configuration
public class FlywayConfig {
    /** 创建并在应用初始化阶段执行迁移。 */
    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .table("seeex_console_schema_history")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();
    }
}
