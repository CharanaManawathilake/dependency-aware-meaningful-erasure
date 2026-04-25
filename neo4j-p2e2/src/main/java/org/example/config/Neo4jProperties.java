package org.example.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class Neo4jProperties {

    @Value("${neo4j.uri}")
    public String uri;

    @Value("${neo4j.username}")
    public String username;

    @Value("${neo4j.password}")
    public String password;

    @Value("${neo4j.database}")
    public String database;
}