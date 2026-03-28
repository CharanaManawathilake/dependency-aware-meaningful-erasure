package org.example;

import org.example.GraphDependencyRules.Cell;
import org.example.GraphDependencyRules.Property;
import org.example.GraphDependencyRules.Rule;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.neo4j.driver.exceptions.Neo4jException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

public class Instantiator {
    final public HashMap<Property, ArrayList<Rule>> propertyInHead;
    final public HashMap<Property, ArrayList<Rule>> propertyInTail;
    final HashMap<String, String> nodeName2keyProp;
    final ArrayList<Rule> EMPTY_LIST = new ArrayList<>(0);
    final String IT_RELATION = ConfigParameter.insertionTimeRelationship;

    public final Driver driver;
    public final SessionConfig sessionConfig;

    public Instantiator(HashMap<Property, ArrayList<Rule>> propertyInHead, HashMap<Property, ArrayList<Rule>> propertyInTail, HashMap<String, String> nodeName2keyProp) throws SQLException {
        this.propertyInHead = propertyInHead;
        this.propertyInTail = propertyInTail;
        this.nodeName2keyProp = nodeName2keyProp;

        driver = GraphDatabase.driver(
                ConfigParameter.connectionUrl,
                AuthTokens.basic(ConfigParameter.username, ConfigParameter.password)
        );

        sessionConfig = SessionConfig.builder().withDatabase(ConfigParameter.database).build();
    }

    public void close() {
        driver.close();
    }

    public ArrayList<String> getKeys(Property prop) {
        String keyProperty = nodeName2keyProp.get(prop.node);
        if (keyProperty == null) {
            throw new RuntimeException("No key mapping for node: " + prop.node);
        }

        try (Session session = driver.session(sessionConfig)) {
            return session.executeRead(tx -> {
                ArrayList<String> list = new ArrayList<>();
                String cypher = String.format(
                        "MATCH (p:%s) RETURN p.%s AS key ORDER BY rand() LIMIT %d",
                        prop.node, keyProperty, ConfigParameter.numKeys
                );

                Result result = tx.run(cypher);
                while (result.hasNext()) {
                    Record record = result.next();
                    list.add(record.get("key").toString());
                }
                return list;
            });
        }
    }

    public void completePropVal(Cell cell) throws Neo4jException {
        var keyProp = nodeName2keyProp.get(cell.property.node);
        var prop = cell.property.property;
        var node = cell.property.node;
        var key = cell.key;

        try (Session session = driver.session(sessionConfig)) {
            session.executeRead(tx -> {
                String cypher = String.format(
                        "MATCH (a:%s {%s:%s})-[:%s]->(b) RETURN a.%s AS aProp, b.%s AS bProp",
                        node, keyProp, key, IT_RELATION, prop, prop
                );

                Result result = tx.run(cypher);
                if (result.hasNext()) {
                    Record record = result.next();
                    cell.value = record.get("aProp").toString();
                    cell.insertionTime = record.get("bProp").asLong();
                }
                if (result.hasNext()) {
                    throw new Neo4jException("Non-unique key!");
                }
                return null;
            });
        }
    }
}
