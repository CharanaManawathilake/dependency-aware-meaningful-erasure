package org.example;

public class ConfigParameter {
    static String dataset = "twitter";
    static String configPath = "src/main/resources/graph_dependency_rules";
    static String ruleFile = "rules_" + dataset + ".csv";
    static String schemaFile = "schema_" + dataset + ".csv";
    static String derivedFile = "derived_" + dataset + ".csv";

    public static String connectionUrl = "neo4j://127.0.0.1:7687"; // Note final slash
    public static String database = "p2e2";
    public static String username = "neo4j";
    public static String password = "password";
    static int numKeys = 10;
    static boolean batching = false;
    static boolean scheduling = false;
    static boolean averageDependence = false;
    static int[] batchSizes = new int[]{};
    static boolean isBatchSizeTime = false;
    static boolean measureMemory = true;
    static long startSchedule = 1;
    static long endSchedule = 2;
    static long baseFrequency = 1000;

    static String insertionTimeRelationship = "INSERTED_AT";


    public static void setDataset(String dataset) {
        ConfigParameter.dataset = dataset;
        ConfigParameter.ruleFile = "rules_" + dataset + ".csv";
        ConfigParameter.schemaFile = "schema_" + dataset + ".csv";
        ConfigParameter.derivedFile = "derived_" + dataset + ".csv";
    }
}