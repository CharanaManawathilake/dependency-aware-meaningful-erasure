package org.example;

import org.example.GraphDependencyRules.Cell;
import org.example.GraphDependencyRules.Cell.HyperEdge;
import org.example.GraphDependencyRules.Property;
import org.example.GraphDependencyRules.Rule;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.neo4j.driver.exceptions.Neo4jException;

import java.sql.ResultSet;
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
            queryRule(rule, start, sourceInsertionTime);
//            try (queryRule(rule, start, sourceInsertionTime)) {
//                result.addAll(resultSetToCellList(rule, start, rs, sourceInsertionTime));
//            }
        }
    }

    public void queryRule(Rule rule, Cell identifier, long sourceInsertionTime) throws Neo4jException {
        ArrayList<String> matchStrings = new ArrayList<>();
        ArrayList<String> whereStrings = new ArrayList<>();
        ArrayList<String> returnStrings = new ArrayList<>();
        // MATCH (p1:Profile) , (p1)-[:INSERTED_AT]->(p1_it), (p1 {profid:4001})
        //WHERE p1_it.mostusedhashtag >= 1643870651000 OR p1_it.avgreplies >= 1643870651000
        //RETURN p1.profid,p1.mostusedhashtag,p1_it.mostusedhashtag,p1.avgreplies,p1_it.avgreplies


        String id = identifier.key;
        String identifierNode = identifier.property.node;
        String nodeKey = nodeName2keyProp.get(identifierNode);
        String idMatchCondition = "(" + identifierNode + "{" + nodeKey + ":" + id + "})";
        String whereIdCondition = identifierNode + "." + nodeKey;

        matchStrings.add(idMatchCondition);
        returnStrings.add(whereIdCondition);
        matchStrings.add(rule.condition);

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

        for (Property tail : rule.tail){
            String tailAlias = rule.node2Alias.get(tail.node);
            String tailAlias_it = tailAlias  + "_it";
            String tailProp = tail.property;

            whereStrings.add(tailAlias + "." + tailProp + ">=" + it);
            returnStrings.add(tailAlias + "." + tailProp);
            returnStrings.add(tailAlias_it + "." + tailProp);
        }

        String finalQuery =
                "MATCH " + String.join(", ", matchStrings) + " " +
                "WHERE " + String.join(" OR ", whereStrings) + " " +
                "RETURN " + String.join(", ", returnStrings) + ";";

        // Return statement
    }

//    // TODO:Needs Completion
//    public ArrayList<HyperEdge> resultSetToCellList(Rule rule, Cell start, ResultSet resultSet, long sourceInsertionTime) throws Neo4jException {
//        var result = new ArrayList<HyperEdge>();
//        while (resultSet.next()) { // For each element in the result
//            HashMap<String, String> table2Key = new HashMap<>(rule.nodes.size(), 1f);
//            int columnIdx = 1;
//            for (int tableIdx = 0; tableIdx < rule.nodes.size(); tableIdx++) {
//                table2Key.put(rule.nodes.get(tableIdx), resultSet.getString(columnIdx++));
//            }
//
//            if (rule.head.equals(start.property)) {
//                // if start == head, then all other cells need to be connected
//                columnIdx += 2;
//                var list = new HyperEdge(rule.tail.size());
//                boolean anyNull = false;
//                for (int tailIdx = 0; tailIdx < rule.tail.size(); tailIdx++) {
//                    var currAttr = rule.tail.get(tailIdx);
//                    var val = resultSet.getString(columnIdx++);
//                    var it = resultSet.getLong(columnIdx++);
//                    if (val == null) {
//                        anyNull = true;
//                        break;
//                    }
//                    if (it >= sourceInsertionTime) {
//                        list.add(new Cell(currAttr, table2Key.get(currAttr.node), val));
//                    }
//                }
//                if (!anyNull && !list.isEmpty()) {
//                    result.add(list);
//                }
//            } else {
//                // if start is in tail, only the head is interesting to us
//                var val = resultSet.getString(columnIdx++);
//                var it = resultSet.getLong(columnIdx);
//                if (val != null && it >= sourceInsertionTime) {
//                    var list = new HyperEdge(1);
//                    list.add(new Cell(rule.head, table2Key.get(rule.head.node), val));
//                    result.add(list);
//                }
//            }
//        }
//        return result;
//    }
}
