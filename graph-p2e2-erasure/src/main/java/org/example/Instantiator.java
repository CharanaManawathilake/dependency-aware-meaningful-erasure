package org.example;

import org.example.GraphDependencyRules.Cell;
import org.example.GraphDependencyRules.Cell.HyperEdge;
import org.example.GraphDependencyRules.Property;
import org.example.GraphDependencyRules.Rule;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.neo4j.driver.exceptions.Neo4jException;
import org.neo4j.driver.summary.ResultSummary;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;

public class Instantiator {
    final public HashMap<Property, ArrayList<Rule>> propertyInHead;
    final public HashMap<Property, ArrayList<Rule>> propertyInTail;
    final HashMap<String, String> nodeName2keyProp;
    final ArrayList<Rule> EMPTY_LIST = new ArrayList<>(0);
    final String IT_RELATION = ConfigParameter.insertionTimeRelationship;

    public final Driver driver;
    public final SessionConfig sessionConfig;

    public Instantiator(HashMap<Property, ArrayList<Rule>> propertyInHead, HashMap<Property, ArrayList<Rule>> propertyInTail, HashMap<String, String> nodeName2keyProp) throws Neo4jException {
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

    public ArrayList<HyperEdge> instantiateAttachedCells(Cell start, long sourceInsertionTime) throws Neo4jException {
        var result = new ArrayList<HyperEdge>();
        iterateRules(start, sourceInsertionTime, result, propertyInHead);
        iterateRules(start, sourceInsertionTime, result, propertyInTail);
        return result;
    }

    public void iterateRules(Cell start, long sourceInsertionTime, ArrayList<HyperEdge> result, HashMap<Property, ArrayList<Rule>> connectedRules)
            throws Neo4jException {
        for (var rule : connectedRules.getOrDefault(start.property, EMPTY_LIST)) {
            ArrayList<Record> rs = queryRule(rule, start, sourceInsertionTime);
            result.addAll(resultSetToCellList(rule, start, rs, sourceInsertionTime));
        }
    }

    public ArrayList<Record> queryRule(Rule rule, Cell identifier, long sourceInsertionTime) throws Neo4jException {
        ArrayList<String> matchStrings = new ArrayList<>();
        ArrayList<String> whereStrings = new ArrayList<>();
        ArrayList<String> returnStrings = new ArrayList<>();

        String id = identifier.key;
        String identifierNode = identifier.property.node;
        String nodeAlias = rule.node2Alias.get(identifierNode);
        String nodeKey = nodeName2keyProp.get(identifierNode);
        String idMatchCondition = "(" + nodeAlias + ":" + identifierNode + " {" + nodeKey + ":" + id + "})";

        matchStrings.add(idMatchCondition);
        matchStrings.add(rule.condition);

        for (int i = 0; i < rule.nodes.size(); i++) {
            String node = rule.nodes.get(i);
            returnStrings.add(rule.node2Alias.get(node) + "." + nodeName2keyProp.get(node));
        }

        for (String node : rule.nodes) {
            String alias = rule.node2Alias.get(node);
            String alias_it = alias + "_it";
            String matchCondition = "(" + alias + ")-[:" + IT_RELATION + "]->(" + alias_it + ")";
            matchStrings.add(matchCondition);
        }

        String it = Long.toString(identifier.insertionTime);
        String headAlias = rule.node2Alias.get(rule.head.node);
        String headAlias_it = headAlias + "_it";
        String headProp = rule.head.property;

        whereStrings.add(headAlias_it + "." + headProp + ">=" + it);
        returnStrings.add(headAlias + "." + headProp);
        returnStrings.add(headAlias_it + "." + headProp);

        for (Property tail : rule.tail) {
            String tailAlias = rule.node2Alias.get(tail.node);
            String tailAlias_it = tailAlias + "_it";
            String tailProp = tail.property;

            whereStrings.add(tailAlias_it + "." + tailProp + ">=" + it);
            returnStrings.add(tailAlias + "." + tailProp);
            returnStrings.add(tailAlias_it + "." + tailProp);
        }

        String finalQuery =
                "MATCH " + String.join(", ", matchStrings) + " " +
                        "WHERE " + String.join(" OR ", whereStrings) + " " +
                        "RETURN " + String.join(", ", returnStrings) + ";";

        try (Session session = driver.session(sessionConfig)) {
            return session.executeRead(tx -> {
                Result rs = tx.run(finalQuery);
                ArrayList<Record> list = new ArrayList<>();
                while (rs.hasNext()) {
                    list.add(rs.next());
                }
                return list;
            });
        }
    }

    public ArrayList<HyperEdge> resultSetToCellList(Rule rule, Cell start, ArrayList<Record> records, long sourceInsertionTime) throws Neo4jException {
        var result = new ArrayList<HyperEdge>();
        for (Record record : records) {
            HashMap<String, String> node2Key = new HashMap<>();
            int columnIdx = 0;
            for (int nodeIdx = 0; nodeIdx < rule.nodes.size(); nodeIdx++) {
                node2Key.put(rule.nodes.get(nodeIdx), record.get(columnIdx++).toString());
            }

            if (rule.head.equals(start.property)) {
                columnIdx += 2;
                var list = new HyperEdge(rule.tail.size());
                boolean anyNull = false;
                for (int tailIdx = 0; tailIdx < rule.tail.size(); tailIdx++) {
                    var currProp = rule.tail.get(tailIdx);
                    String val = record.get(columnIdx++).toString();
                    long it = record.get(columnIdx++).asLong();
                    if (val == null) {
                        anyNull = true;
                        break;
                    }
                    if (it >= sourceInsertionTime) {
                        list.add(new Cell(currProp, node2Key.get(currProp.node), val));
                    }
                }
                if (!anyNull && !list.isEmpty()) {
                    result.add(list);
                }
            } else {
                String val = record.get(columnIdx++).toString();
                long it = record.get(columnIdx).asLong();
                if (val != null && it >= sourceInsertionTime) {
                    var list = new HyperEdge(1);
                    list.add(new Cell(rule.head, node2Key.get(rule.head.node), val));
                    result.add(list);
                }
            }
        }
        return result;
    }

    public long deleteCells(HashSet<Cell> toDelete) throws Neo4jException {
        var delStart = System.nanoTime();
        for (var cell : toDelete) {
            setToNull(cell);
        }
        return System.nanoTime() - delStart;
    }

    private void setToNull(Cell cell) throws Neo4jException {
        String node = cell.property.node;
        String prop = cell.property.property;
        String keyProp = nodeName2keyProp.get(node);
        String key = cell.key;

        String query = String.format(
                "MATCH (a:%s {%s: %s}) REMOVE a.%s",
                node, keyProp, key, prop
        );

        try (Session session = driver.session(sessionConfig)) {
            session.executeWrite(tx -> {
                Result rs = tx.run(query);
                ResultSummary summary = rs.consume();
                if (summary.counters().propertiesSet() > 1) {
                    throw new Neo4jException("Given id is not unique");
                }
                return null;
            });
        }
    }

    public void resetValues(Collection<Cell> cells) throws Neo4jException {
        try (Session session = driver.session(sessionConfig)) {
            session.executeWrite(tx -> {

                for (var cell : cells) {

                    String node = cell.property.node;
                    String property = cell.property.property;
                    String keyColumn = nodeName2keyProp.get(cell.property.node);

                    String cypher =
                            "MATCH (n:" + node + " {" + keyColumn + ": $key}) " +
                                    "SET n." + property + " = $value";

                    Object value;

                    if (property.equals("payload")) {
                        value = cell.value; // JSON stored as string or pre-parsed map
                    } else {
                        try {
                            value = Long.parseLong(cell.value);
                        } catch (Exception e) {
                            try {
                                value = Float.parseFloat(cell.value);
                            } catch (Exception e2) {
                                value = cell.value;
                            }
                        }
                    }

                    Result rs = tx.run(
                            cypher,
                            org.neo4j.driver.Values.parameters(
                                    "key", cell.key,
                                    "value", value
                            )
                    );

                    ResultSummary summary = rs.consume();

                    if (summary.counters().propertiesSet() != 1) {
                        throw new Neo4jException("Unexpected number of updates");
                    }
                }

                return null;
            });
        }
    }
}
