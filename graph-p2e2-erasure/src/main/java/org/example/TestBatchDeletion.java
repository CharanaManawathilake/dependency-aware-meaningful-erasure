// TestBatchDeletion.java
// a standalone java file to test batch deletion functionality of erase.java
package org.example;

import org.example.GraphDependencyRules.Cell;
import org.example.GraphDependencyRules.Property;
import java.util.HashSet;
import java.util.HashMap;

public class TestBatchDeletion {
    public static void main(String[] args) throws Exception {
        // Setup config from environment variables or use default placeholders
        ConfigParameter.connectionUrl = System.getenv().getOrDefault("NEO4J_URI", "bolt://127.0.0.1:7687");
        ConfigParameter.database = System.getenv().getOrDefault("NEO4J_DATABASE", "neo4j");
        ConfigParameter.username = System.getenv().getOrDefault("NEO4J_USERNAME", "neo4j");
        ConfigParameter.password = System.getenv().getOrDefault("NEO4J_PASSWORD", "<your_password>");

        System.out.println("Connecting to Neo4j at " + ConfigParameter.connectionUrl + "...");

        HashMap<Property, java.util.ArrayList<org.example.GraphDependencyRules.Rule>> emptyMap = new HashMap<>();
        HashMap<String, String> keyMap = new HashMap<>();
        keyMap.put("Profile", "profid");
        keyMap.put("Post", "tweetid");

        Instantiator instantiator = new Instantiator(emptyMap, emptyMap, keyMap);

        // here, sampling 3 random keys directly from the DB
        System.out.println("Sampling 3 random Profile keys from DB...");
        ConfigParameter.numKeys = 3;
        java.util.ArrayList<String> sampleKeys = instantiator.getKeys(new Property("Profile", "avglikes"));

        if (sampleKeys.isEmpty()) {
            System.out.println("No keys found! Is the DB populated?");
            instantiator.close();
            return;
        }

        HashSet<Cell> toDelete = new HashSet<>();
        for (String key : sampleKeys) {
            System.out.println("Queueing deletion for Profile [profid=" + key + "] property: 'avglikes'");
            toDelete.add(new Cell(new Property("Profile", "avglikes"), key));
        }

        System.out.println("Executing batched deleteCells()...");
        long time = instantiator.deleteCells(toDelete);

        System.out.println("Success! Deleted " + toDelete.size() + " properties in "
                + (time / 1000000.0) + " ms!");

        instantiator.close();
        System.exit(0);
    }
}
