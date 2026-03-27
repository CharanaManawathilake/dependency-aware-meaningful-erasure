package org.example;

import com.gurobi.gurobi.GRB;
import com.gurobi.gurobi.GRBEnv;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.example.GraphDependencyRules.Cell;
import org.example.GraphDependencyRules.Property;
import org.example.GraphDependencyRules.Rule;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {
    final static ArrayList<Rule> rules = new ArrayList<>();
    final static ArrayList<Rule> derivedData = new ArrayList<>();
    final static HashSet<Property> derivedProperties = new HashSet<>();
    final static HashMap<Property, ArrayList<Rule>> propertyInHead = new HashMap<>();
    final static HashMap<Property, ArrayList<Rule>> propertyInTail = new HashMap<>();
    final static HashMap<String, String> nodeName2keyCol = new HashMap<>();
    final static ArrayList<Cell.HyperEdge> EMPTY_LIST = new ArrayList<>(0);
    static GRBEnv env;

    public static void main(String[] args) throws Exception {
        String configFilePath = args.length > 0 ? args[0] : "config.json";
        parseConfigFile(Files.readString(Paths.get(configFilePath)));

        env = new GRBEnv();
        env.set(GRB.IntParam.OutputFlag, 0);
        env.set(GRB.IntParam.LogToConsole, 0);

        parseRules();
        parseSchema();
        parseDerivedData();

        var baseProperties = new HashSet<Property>();
        baseProperties.addAll(propertyInTail.keySet());
        baseProperties.addAll(propertyInHead.keySet());
        baseProperties.removeAll(derivedProperties);

        checkNonCyclicRules(baseProperties);

        var instantiator = new Instantiator(propertyInHead, propertyInTail, nodeName2keyCol);

        // TODO:Complete conditional access
        iterateProperties(instantiator, baseProperties);
    }

    private static void parseConfigFile(String jsonString) throws Exception {
        JSONObject root = new JSONObject(jsonString);

        if (root.has("dataset")) {
            ConfigParameter.setDataset(root.getString("dataset"));
        }
        if (root.has("configPath")) {
            ConfigParameter.configPath = root.getString("configPath");
        }
        if (root.has("ruleFile")) {
            ConfigParameter.ruleFile = root.getString("ruleFile");
        }
        if (root.has("schemaFile")) {
            ConfigParameter.schemaFile = root.getString("schemaFile");
        }
        if (root.has("derivedFile")) {
            ConfigParameter.derivedFile = root.getString("derivedFile");
        }
        if (root.has("batching")) {
            ConfigParameter.batching = root.getBoolean("batching");
        }
        if (root.has("scheduling")) {
            ConfigParameter.scheduling = root.getBoolean("scheduling");
        }
        if (root.has("averageDependence")) {
            ConfigParameter.averageDependence = root.getBoolean("averageDependence");
        }
        if (root.has("numKeys")) {
            ConfigParameter.numKeys = root.getInt("numKeys");
        }
        if (root.has("batchSizes")) {
            var jsonArray = root.getJSONArray("batchSizes");
            ConfigParameter.batchSizes = new int[jsonArray.length()];
            for (int i = 0; i < jsonArray.length(); i++) {
                ConfigParameter.batchSizes[i] = jsonArray.getInt(i);
            }
        }
        if (root.has("isBatchSizeTime")) {
            ConfigParameter.isBatchSizeTime = root.getBoolean("isBatchSizeTime");
        }
        if (root.has("connectionUrl")) {
            ConfigParameter.connectionUrl = root.getString("connectionUrl");
        }
        if (root.has("database")) {
            ConfigParameter.database = root.getString("database");
        }
        if (root.has("username")) {
            ConfigParameter.username = root.getString("username");
        }
        if (root.has("password")) {
            ConfigParameter.password = root.getString("password");
        }
        if (root.has("startSchedule")) {
            ConfigParameter.startSchedule = root.getLong("startSchedule");
        }
        if (root.has("endSchedule")) {
            ConfigParameter.endSchedule = root.getLong("endSchedule");
        }
        if (root.has("baseFrequency")) {
            ConfigParameter.baseFrequency = root.getLong("baseFrequency");
        }
    }

    private static void parseRules() throws Exception {
        var parser = CSVFormat.DEFAULT.parse(Files.newBufferedReader(Paths.get(ConfigParameter.configPath, ConfigParameter.ruleFile)));
        for (var record : parser) {
            var rule = parseRule(record);
            rules.add(rule);
            propertyInHead.computeIfAbsent(rule.head, a -> new ArrayList<>()).add(rule);
            for (var tail : rule.tail) {
                propertyInTail.computeIfAbsent(tail, property -> new ArrayList<>()).add(rule);
            }
        }
    }

    public static Rule parseRule(CSVRecord record) throws Exception {
        if (!record.get(0).equals("MATCH") || !record.get(2).equals("RETURN")) {
            throw new Exception("Invalid rule format");
        }

        Rule rule = new Rule();
        rule.condition = record.get(1);

        Pattern pattern = Pattern.compile("\\(([A-Za-z0-9_]+):([A-Za-z0-9_]+)\\)");
        Matcher matcher = pattern.matcher(record.get(1));

        while (matcher.find()) {
            String left = matcher.group(1);
            String right = matcher.group(2);
            rule.node2Alias.put(left, right);
            rule.node2Alias.put(right, left);
        }

        for (int i = 3; i < record.size(); i++) {
            String s = record.get(i);

            int first = s.indexOf('.');
            int last = s.lastIndexOf('.');

            if (first != last || first <= 0 || first == s.length() - 1) {
                throw new IllegalArgumentException("Invalid property format: " + s);
            }

            String node = s.substring(0, first);
            String property = s.substring(first + 1);

            if (i == 3) {
                rule.head = new Property(rule.node2Alias.get(node), property);
            } else {
                rule.tail.add(new Property(rule.node2Alias.get(node), property));
            }
        }

        return rule;
    }

    private static void parseSchema() throws IOException {
        var parser = CSVFormat.DEFAULT.parse(Files.newBufferedReader(Paths.get(ConfigParameter.configPath, ConfigParameter.schemaFile)));
        for (var record : parser) {
            nodeName2keyCol.put(record.get(0), record.get(1));
        }
    }

    private static void parseDerivedData() throws Exception {
        var parser = CSVFormat.DEFAULT.parse(Files.newBufferedReader(Paths.get(ConfigParameter.configPath, ConfigParameter.derivedFile)));
        for (var record : parser) {
            var rule = parseRule(record);
            derivedData.add(rule);
            derivedProperties.add(rule.head);
        }
    }

    private static void checkNonCyclicRules(HashSet<Property> allProperties) {
        HashSet<Property> checkedProperties = new HashSet<>();
        for (var startProperty : allProperties) {
            if (checkedProperties.contains(startProperty)) {
                continue;
            }
            checkedProperties.add(startProperty);

            Queue<Property> propertiesToVisit = new LinkedList<>();
            propertiesToVisit.add(startProperty);

            HashMap<Property, Property> prop2Parent = new HashMap<>();
            prop2Parent.put(startProperty, new Property("", ""));

            while (!propertiesToVisit.isEmpty()) {
                var curr = propertiesToVisit.poll();

                for (var rule : propertyInHead.getOrDefault(curr, new ArrayList<>(0))) {
                    for (var prop : rule.tail) {
                        assert (prop2Parent.get(prop) == null || prop2Parent.get(curr).equals(prop)) || prop.equals(curr);
                        if (!prop.equals(curr)) {
                            prop2Parent.put(prop, curr);
                        }
                        if (checkedProperties.add(prop)) {
                            propertiesToVisit.add(prop);
                        }
                    }
                }

                for (var rule : propertyInTail.getOrDefault(curr, new ArrayList<>(0))) {
                    var prop = rule.head;

                    assert (prop2Parent.get(prop) == null || prop2Parent.get(curr).equals(prop)) || prop.equals(curr);

                    if (!prop.equals(curr)) {
                        prop2Parent.put(prop, curr);
                    }
                    if (checkedProperties.add(prop)) {
                        propertiesToVisit.add(prop);
                    }
                }
            }
        }
    }

    private static void iterateProperties(Instantiator instantiator, Set<Property> properties) throws Exception {
        writeHeader();
        HashSet<Cell>[] deletionSets = new HashSet[3];

        for (var prop : properties){
            System.out.print(prop.toString() + ",");

            var keys = instantiator.getKeys(prop);
        }
    }

    private static void writeHeader() {
        // TODO:Add suitable values
        System.out.println("Attribute,optimalTime,optimalInstantiationTime,optimalModelTime,optimalOptimizationTime,optimalDeletionTime,approximateTime,approximateInstantiationTime,approximateModelTime,approximateOptimizationTime,approximateDeletionTime,ilpTime,ilpInstantiationTime,ilpModelTime,ilpOptimizationTime,ilpDeletionTime,optimalDeletes,optimalInstantiations,optimalHeight,optimalMemory,approximateDeletes,approximateInstantiations,approximateHeight,approximateMemory,ilpDeletes,ilpInstantiations,ilpHeight,ilpMemory");
    }
}