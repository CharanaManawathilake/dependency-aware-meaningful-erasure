package org.example.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class Neo4jConfig {

    @Bean
    public Driver driver(
            Neo4jProperties props
    ) {

        return GraphDatabase.driver(
                props.uri,
                AuthTokens.basic(
                        props.username,
                        props.password
                )
        );
    }
}
