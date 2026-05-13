package org.example.service;

import org.example.config.Neo4jProperties;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Service
public class Neo4jForwardingService {

    @Autowired
    private Driver driver;

    @Autowired
    private Neo4jProperties properties;

    public List<HashMap<String, Object>>
    execute(String query) {

        SessionConfig sessionConfig =
                SessionConfig.builder()
                        .withDatabase(
                                properties.database
                        )
                        .build();

        try (
                Session session =
                        driver.session(sessionConfig)
        ) {

            return session.executeRead(tx -> {

                Result result = tx.run(query);

                List<HashMap<String, Object>>
                        response =
                        new ArrayList<>();

                while (result.hasNext()) {

                    Record record =
                            result.next();

                    HashMap<String, Object>
                            row =
                            new HashMap<>();

                    for (String key : record.keys()) {

                        var value = record.get(key);

                        switch (value.type().name()) {

                            case "NODE":

                                var node = value.asNode();

                                HashMap<String, Object> nodeMap =
                                        new HashMap<>();

                                nodeMap.put("id", node.id());

                                nodeMap.put(
                                        "labels",
                                        node.labels()
                                );

                                nodeMap.put(
                                        "properties",
                                        node.asMap()
                                );

                                row.put(key, nodeMap);

                                break;

                            case "RELATIONSHIP":

                                var rel = value.asRelationship();

                                HashMap<String, Object> relMap =
                                        new HashMap<>();

                                relMap.put("id", rel.id());

                                relMap.put(
                                        "type",
                                        rel.type()
                                );

                                relMap.put(
                                        "properties",
                                        rel.asMap()
                                );

                                row.put(key, relMap);

                                break;

                            default:

                                row.put(
                                        key,
                                        value.asObject()
                                );
                        }
                    }

                    response.add(row);
                }

                return response;
            });
        }
    }
}