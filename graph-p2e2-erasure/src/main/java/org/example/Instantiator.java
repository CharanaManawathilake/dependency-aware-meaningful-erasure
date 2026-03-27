package org.example;

import org.example.GraphDependencyRules.Property;
import org.example.GraphDependencyRules.Rule;
import org.neo4j.driver.*;

import java.lang.Record;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;

public class Instantiator {
    final public HashMap<Property, ArrayList<Rule>> propertyInHead;
    final public HashMap<Property, ArrayList<Rule>> propertyInTail;
    final HashMap<String, String> nodeName2keyCol;
    final ArrayList<Rule> EMPTY_LIST = new ArrayList<>(0);
    final String IT_SUFFIX = "Insertion";

    public final Driver driver;

    public Instantiator(HashMap<Property, ArrayList<Rule>> propertyInHead, HashMap<Property, ArrayList<Rule>> propertyInTail, HashMap<String, String> nodeName2keyCol) throws SQLException {
        this.propertyInHead = propertyInHead;
        this.propertyInTail = propertyInTail;
        this.nodeName2keyCol = nodeName2keyCol;

        driver = GraphDatabase.driver(
                ConfigParameter.connectionUrl,
                AuthTokens.basic(ConfigParameter.username, ConfigParameter.password)
        );
    }

    public void close() {
        driver.close();
    }

    public ArrayList<String> getKeys(Property prop) {
        ArrayList<String> keys;

        try (Session session = driver.session(SessionConfig.defaultConfig())) {
            keys = session.beginTransaction((Transaction tx) -> {
                ArrayList<String> list = new ArrayList<>();

                String cypher = String.format(
                        "MATCH (p:%s) RETURN p.%s AS key ORDER BY rand() LIMIT 10",
                        prop.node, prop.property
                );

                Result result = tx.run(cypher);
                for (Record record : result.list()) {
                    list.add(record.get("key").asString());
                }

                return list;
            });
        }

        return keys;
    }
}
