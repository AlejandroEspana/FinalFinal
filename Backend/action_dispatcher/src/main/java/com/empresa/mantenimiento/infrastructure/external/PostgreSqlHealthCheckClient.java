package com.empresa.mantenimiento.infrastructure.external;

import org.springframework.stereotype.Component;

@Component
public class PostgreSqlHealthCheckClient {

    public String getTargetDatabaseEngine() {
        return "PostgreSQL 16 Relational Engine";
    }
}
