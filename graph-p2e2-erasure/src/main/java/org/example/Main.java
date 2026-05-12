package org.example;

import com.gurobi.gurobi.*;
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
        List<JSONObject> cases = parseCases(Files.readString(Paths.get(configFilePath)));

        env = new GRBEnv();
        env.set(GRB.IntParam.OutputFlag, 0);
        env.set(GRB.IntParam.LogToConsole, 0);

        for (JSONObject caseConfig : cases) {
            rules.clear();
            derivedData.clear();
            derivedProperties.clear();
            propertyInHead.clear();
            propertyInTail.clear();
            nodeName2keyCol.clear();

            parseConfigFile(caseConfig.toString());
            parseRules();
            parseSchema();
            parseDerivedData();

            var baseProperties = new HashSet<Property>();
            baseProperties.addAll(propertyInTail.keySet());
            baseProperties.addAll(propertyInHead.keySet());
            baseProperties.removeAll(derivedProperties);

            checkNonCyclicRules(baseProperties);

            var instantiator = new Instantiator(propertyInHead, propertyInTail, nodeName2keyCol);
            try {
                iterateProperties(instantiator, baseProperties);
            } finally {
                instantiator.close();
            }
        }

        env.dispose();
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
        if (root.has("insertionTimeRelationship")) {
            ConfigParameter.insertionTimeRelationship = root.getString("insertionTimeRelationship");
        }
        if (root.has("algorithms")) {
            var arr = root.getJSONArray("algorithms");
            ConfigParameter.algorithms = new HashSet<>();
            for (int i = 0; i < arr.length(); i++) {
                ConfigParameter.algorithms.add(arr.getString(i).toLowerCase());
            }
        } else {
            ConfigParameter.algorithms = new HashSet<>(Arrays.asList("optimal", "approximate", "ilp", "greedy"));
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

    private static List<JSONObject> parseCases(String jsonString) {
        JSONObject root = new JSONObject(jsonString);
        List<JSONObject> cases = new ArrayList<>();

        if (root.has("cases")) {
            var arr = root.getJSONArray("cases");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject c = arr.getJSONObject(i);
                if (c.optBoolean("enabled", true)) {
                    cases.add(c);
                }
            }
        } else {
            cases.add(root);
        }
        return cases;
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
            rule.nodes.add(right);
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
        HashSet<Cell>[] deletionSets = new HashSet[4];

        for (var prop : properties){
            System.out.print(prop.toString() + ",");

            ArrayList<String> keys = instantiator.getKeys(prop);
             for (String key : keys ){
                 var deletionPropVal = new Cell(prop, key);
                 instantiator.completePropVal(deletionPropVal);
                 InstantiatedModel instantiatedModel = new InstantiatedModel(deletionPropVal, instantiator);
                 var algos = ConfigParameter.algorithms;

                 if (algos.contains("optimal"))
                     deletionSets[0] = runDeletionMethod(deletionPropVal, instantiatedModel, 0, Utils.optimalCounts);
                 if (algos.contains("approximate"))
                     deletionSets[1] = runDeletionMethod(deletionPropVal, instantiatedModel, 1, Utils.approximateCounts);
                 if (algos.contains("ilp"))
                     deletionSets[2] = runDeletionMethod(deletionPropVal, instantiatedModel, 2, Utils.ilpCounts);
                 if (algos.contains("greedy"))
                     deletionSets[3] = runDeletionMethod(deletionPropVal, instantiatedModel, 3, Utils.greedyCounts);

                 HashSet<Cell> referenceSet = deletionSets[2] != null ? deletionSets[2]
                         : deletionSets[0] != null ? deletionSets[0]
                         : deletionSets[3] != null ? deletionSets[3]
                         : deletionSets[1];

                 var deletionTime = instantiator.deleteCells(referenceSet);
                 instantiator.resetValues(referenceSet);

                 if (algos.contains("optimal")) {
                     Utils.optimalTimes[4] += deletionSets[0].size() == referenceSet.size()
                             ? deletionTime : instantiator.deleteCells(deletionSets[0]);
                     if (deletionSets[0].size() != referenceSet.size())
                         instantiator.resetValues(deletionSets[0]);
                 }
                 if (algos.contains("approximate")) {
                     Utils.approximateTimes[4] += deletionSets[1].size() == referenceSet.size()
                             ? deletionTime : instantiator.deleteCells(deletionSets[1]);
                     if (deletionSets[1].size() != referenceSet.size())
                         instantiator.resetValues(deletionSets[1]);
                 }
                 if (algos.contains("ilp")) {
                     Utils.ilpTimes[4] += deletionTime;
                 }
                 if (algos.contains("greedy")) {
                     Utils.greedyTimes[4] += deletionSets[3].size() == referenceSet.size()
                             ? deletionTime : instantiator.deleteCells(deletionSets[3]);
                     if (deletionSets[3].size() != referenceSet.size())
                         instantiator.resetValues(deletionSets[3]);
                 }
             }
            writeOutput();
        }
    }

    private static HashSet<Cell> runDeletionMethod(Cell deleted, InstantiatedModel instantiatedModel, int deletionMethod, long[] countsArray) throws Exception {
        HashSet<Cell> result = null;
        switch (deletionMethod) {
            case 0:
                result = optimalDelete(instantiatedModel, deleted);
                break;
            case 1:
                result = approximateDelete(instantiatedModel, deleted);
                break;
            case 2:
                result = ilpApproach(instantiatedModel, deleted);
                break;
            case 3:
                result = greedySetCoverDelete(instantiatedModel, deleted);
                break;
        }
        countsArray[0] += result.size() - 1;
        return result;
    }

    public static HashSet<Cell> optimalDelete(InstantiatedModel model, Cell deleted) {
        Utils.optimalCounts[1] += model.instantiationTime.size() - 1;
        Utils.optimalCounts[2] += model.treeLevels.size();
        Utils.optimalTimes[2] += model.modelConstructionTime;

        var start = System.nanoTime();
        for (var currLevels : model.treeLevels) {
            for (var currCell : currLevels) {
                Utils.optimalTimes[1] += model.instantiationTime.getOrDefault(currCell, 0L);
                var childrenEdges = model.cell2Edge.get(currCell);
                currCell.cost = 1;
                if (childrenEdges != null) {
                    for (var edge : childrenEdges) {
                        long minCost = Integer.MAX_VALUE;
                        Cell minCell = null;

                        for (var cell : edge) {
                            if (minCell == null || cell.cost < minCost) {
                                minCell = cell;
                                minCost = cell.cost;
                            }
                        }
                        edge.minCell = minCell;
                        currCell.cost += minCost;
                    }
                }
            }
        }

        Queue<Cell> cellsToVisit = new LinkedList<>();
        cellsToVisit.add(deleted);

        HashSet<Cell> toDelete = new HashSet<>();
        toDelete.add(deleted);

        while (!cellsToVisit.isEmpty()) {
            var currCell = cellsToVisit.poll();
            var edges = model.cell2Edge.get(currCell);
            if (edges != null) {
                for (var edge : model.cell2Edge.get(currCell)) {
                    if (toDelete.add(edge.minCell)) {
                        cellsToVisit.add(edge.minCell);
                    }
                }
            }
        }
        Utils.optimalTimes[3] += System.nanoTime() - start;
        if (ConfigParameter.measureMemory) {
            Utils.optimalCounts[3] += measureOptimalMemory(model, deleted);
        }

        return toDelete;
    }

    private static long measureOptimalMemory(InstantiatedModel model, Cell deleted) {
        long size = 0;
        LinkedList<Cell> cellsToVisit = new LinkedList<>();
        HashSet<Cell> instantiatedCells = new HashSet<>();
        cellsToVisit.add(deleted);
        while (!cellsToVisit.isEmpty()) {
            var curr = cellsToVisit.poll();
            // per cell: 4 bytes for the table index, 4 bytes for the row index, 4 bytes insertionTime, 1 byte state (deleted) and 4 bytes cost
            size += 4 + 4 + 4 + 1 + 4;
            var edges = model.cell2Edge.get(curr);
            if (edges != null) {
                for (var edge : edges) {
                    // 8 bytes per element in hyperedge + 8 bytes for pointer from head to edge + 4 bytes for the cheapest node
                    size += edge.size() * 8L + 8L + 4L;
                    for (var cell : edge) {
                        if (instantiatedCells.add(cell)) {
                            cellsToVisit.add(cell);
                        }
                    }
                }
            }
        }
        return size;
    }

    private static HashSet<Cell> approximateDelete(InstantiatedModel model, Cell deleted) {
        Cell lastCell = null;
        var start = System.nanoTime();
        HashSet<Cell> edgesInstantiated = new HashSet<>();
        var toDelete = new HashSet<Cell>();
        toDelete.add(deleted);
        HashSet<Cell> nodesInstantiated = new HashSet<>();

        Queue<Cell> cellsToVisit = new LinkedList<>();
        cellsToVisit.add(deleted);
        edgesInstantiated.add(deleted);
        nodesInstantiated.add(deleted);

        while (!cellsToVisit.isEmpty()) {
            var curr = cellsToVisit.poll();

            var edges = model.cell2Edge.get(curr);
            if (edges != null) {
                for (var edge : edges) {
                    Cell minCell = null;
                    nodesInstantiated.addAll(edge);
                    for (var cell : edge) {
                        edgesInstantiated.add(cell);
                        for (var grandChildren : model.cell2Edge.getOrDefault(cell, EMPTY_LIST)) {
                            nodesInstantiated.addAll(grandChildren);
                        }
                        if (minCell == null || model.cell2Edge.getOrDefault(cell, EMPTY_LIST).size() < model.cell2Edge.getOrDefault(minCell, EMPTY_LIST).size()) {
                            minCell = cell;
                            edge.minCell = cell;
                        }
                    }
                    if (toDelete.add(minCell)) {
                        lastCell = minCell;
                        cellsToVisit.add(minCell);
                    }
                }
            }
        }

        Utils.approximateTimes[2] += System.nanoTime() - start;
        Utils.approximateCounts[1] += nodesInstantiated.size() - 1;
        int count = 0;
        for (var level : model.treeLevels) {
            if (level.contains(lastCell)) break;
            count++;
        }
        Utils.approximateCounts[2] += model.treeLevels.size() - count;
        if (ConfigParameter.measureMemory) {
            Utils.approximateCounts[3] += measureApproximateMemory(model, nodesInstantiated, edgesInstantiated);
        }
        for (var cell : edgesInstantiated) {
            Utils.approximateTimes[1] += model.instantiationTime.getOrDefault(cell, 0L);
        }

        return toDelete;
    }

    private static long measureApproximateMemory(InstantiatedModel model, HashSet<Cell> nodesInstantiated, HashSet<Cell> edgesInstantiated) {
        long size = 0;

        // per cell: 4 bytes for the table index, 4 bytes for the row index, 4 bytes insertionTime, 1 byte state (deleted) and 4 bytes cost
        size += nodesInstantiated.size() * (4 + 4 + 4 + 1L);

        for (var cell : edgesInstantiated) {
            var edges = model.cell2Edge.getOrDefault(cell, EMPTY_LIST);
            for (var edge : edges) {
                size += edge.size() * 8L + 8L + 4L;
            }
        }
        return size;
    }

    private static HashSet<Cell> ilpApproach(InstantiatedModel model, Cell deleted) throws GRBException {
        Utils.ilpTimes[2] += model.modelConstructionTime;
        var start = System.nanoTime();
        int maxId = 0;
        int edgeCounter = -1;
        HashMap<Cell, Integer> cell2Id = new HashMap<>();
        HashMap<Cell, GRBVar> cell2Var = new HashMap<>();
        HashSet<Cell> instantiatedCells = new HashSet<>();
        HashSet<Cell> toDelete = new HashSet<>();
        Queue<Cell> cellsToVisit = new LinkedList<>();
        GRBModel grbModel = new GRBModel(env);
        GRBLinExpr obj = new GRBLinExpr();
        cell2Var.put(deleted, grbModel.addVar(1, 1, 0, GRB.BINARY, "a0"));
        cell2Id.put(deleted, maxId++);
        cellsToVisit.add(deleted);
        instantiatedCells.add(deleted);

        while (!cellsToVisit.isEmpty()) {
            var curr = cellsToVisit.poll();
            var currId = cell2Id.get(curr);
            var aj = cell2Var.get(curr);
            obj.addTerm(1, aj);
            Utils.ilpTimes[1] += model.instantiationTime.getOrDefault(curr, 0L);

            var edges = model.cell2Edge.get(curr);
            if (edges != null) {
                for (var edge : edges) {
                    var bi = grbModel.addVar(0, 1, 0, GRB.BINARY, "b" + ++edgeCounter);
                    var hij = grbModel.addVar(0, 1, 0, GRB.BINARY, "h" + edgeCounter + currId);
                    grbModel.addConstr(aj, GRB.EQUAL, hij, "");
                    grbModel.addConstr(bi, GRB.EQUAL, hij, "");
                    var tjiVars = new ArrayList<GRBVar>(edge.size());
                    for (var cell : edge) {
                        int tId;
                        GRBVar aCell;
                        if (cell2Id.get(cell) == null) {
                            tId = maxId;
                            cell2Id.put(cell, maxId++);
                            aCell = grbModel.addVar(0, 1, 0, GRB.BINARY, "a" + tId);
                            cell2Var.put(cell, aCell);
                        } else {
                            tId = cell2Id.get(cell);
                            aCell = cell2Var.get(cell); // grbModel.getVarByName("a" + tId);
                        }

                        var tji = grbModel.addVar(0, 1, 0, GRB.BINARY, "t" + edgeCounter + tId);
                        tjiVars.add(tji);
                        grbModel.addConstr(tji, GRB.EQUAL, aCell, "");
                        if (instantiatedCells.add(cell)) {
                            cellsToVisit.add(cell);
                        }
                    }
                    GRBLinExpr tjis = new GRBLinExpr();
                    for (var tji : tjiVars) {
                        tjis.addTerm(1, tji);
                    }
                    grbModel.addConstr(tjis, GRB.GREATER_EQUAL, bi, "");
                }
            }
        }

        var stop = System.nanoTime();
        Utils.ilpTimes[2] += stop - start;

        grbModel.setObjective(obj, GRB.MINIMIZE);
        grbModel.optimize();

        if (grbModel.get(GRB.IntAttr.Status) == 3) {
            throw new GRBException("Infeasible grbModel");
        }

        for (var cellEntry : cell2Id.entrySet()) {
            if (grbModel.getVarByName("a" + cellEntry.getValue()).get(GRB.DoubleAttr.X) == 1d) {
                toDelete.add(cellEntry.getKey());
            }
        }
        grbModel.dispose();
        Utils.ilpTimes[3] += System.nanoTime() - stop;
        Utils.ilpCounts[1] += cell2Id.size() - 1;

        if (ConfigParameter.measureMemory) {
            Utils.ilpCounts[3] += measureILPMemory(model, deleted);
        }
        return toDelete;
    }

    private static HashSet<Cell> greedySetCoverDelete(InstantiatedModel model, Cell deleted) {
        Utils.greedyTimes[2] += model.modelConstructionTime;
        var start = System.nanoTime();

        HashMap<Cell, HashSet<Cell.HyperEdge>> cell2MemberEdges = new HashMap<>();
        for (var entry : model.cell2Edge.entrySet()) {
            for (var edge : entry.getValue()) {
                for (var cell : edge) {
                    cell2MemberEdges
                            .computeIfAbsent(cell, a -> new HashSet<>())
                            .add(edge);
                }
            }
        }

        HashSet<Cell.HyperEdge> uncoveredEdges = new HashSet<>();
        for (var edges : model.cell2Edge.values()) {
            uncoveredEdges.addAll(edges);
        }

        HashSet<Cell> toDelete = new HashSet<>();
        toDelete.add(deleted);

        // remove edges already covered by root cell
        uncoveredEdges.removeAll(
                cell2MemberEdges.getOrDefault(deleted, new HashSet<>())
        );

        // greedy loop
        while (!uncoveredEdges.isEmpty()) {
            Cell bestCell = null;
            int bestCount = -1;

            for (var entry : cell2MemberEdges.entrySet()) {
                if (toDelete.contains(entry.getKey())) continue;

                int count = 0;
                for (var edge : entry.getValue()) {
                    if (uncoveredEdges.contains(edge)) count++;
                }

                if (count > bestCount) {
                    bestCount = count;
                    bestCell = entry.getKey();
                }
            }

            if (bestCell == null) break;

            toDelete.add(bestCell);
            uncoveredEdges.removeAll(
                    cell2MemberEdges.getOrDefault(bestCell, new HashSet<>())
            );
        }

        Utils.greedyTimes[3] += System.nanoTime() - start;
        Utils.greedyCounts[1] += cell2MemberEdges.size();
        Utils.greedyCounts[2] += model.treeLevels.size();

        if (ConfigParameter.measureMemory) {
            Utils.greedyCounts[3] += measureGreedyMemory(model, cell2MemberEdges);
        }

        return toDelete;
    }

    private static long measureGreedyMemory(InstantiatedModel model, HashMap<Cell, HashSet<Cell.HyperEdge>> cell2MemberEdges) {
        long size = 0;
        size += cell2MemberEdges.size() * (4 + 4 + 4 + 1 + 4L);
        for (var edges : cell2MemberEdges.values()) {
            size += edges.size() * 8L;
        }
        for (var edges : model.cell2Edge.values()) {
            size += edges.size() * 8L;
        }
        return size;
    }

    private static long measureILPMemory(InstantiatedModel model, Cell deleted) {
        long size = 0;
        LinkedList<Cell> cellsToVisit = new LinkedList<>();
        HashSet<Cell> instantiatedCells = new HashSet<>();
        cellsToVisit.add(deleted);
        while (!cellsToVisit.isEmpty()) {
            var curr = cellsToVisit.poll();
            size += 4 + 4 + 4 + 1 + 8;
            var edges = model.cell2Edge.get(curr);
            if (edges != null) {
                for (var edge : edges) {
                    size += 1L + 1L + 16L + 16L + edge.size() * (1L + 16L + 8L) + 8L;
                    for (var cell : edge) {
                        if (instantiatedCells.add(cell)) {
                            cellsToVisit.add(cell);
                        }
                    }
                }
            }
        }
        return size;
    }

    private static void writeHeader() {
        var algos = ConfigParameter.algorithms;
        ArrayList<String> headers = new ArrayList<>();
        headers.add("Dataset");
        headers.add("Attribute");

        if (algos.contains("optimal")) {
            headers.addAll(Arrays.asList(
                    "optimalTime","optimalInstantiationTime","optimalModelTime",
                    "optimalOptimizationTime","optimalDeletionTime",
                    "optimalDeletes","optimalInstantiations","optimalHeight","optimalMemory"
            ));
        }
        if (algos.contains("approximate")) {
            headers.addAll(Arrays.asList(
                    "approximateTime","approximateInstantiationTime","approximateModelTime",
                    "approximateOptimizationTime","approximateDeletionTime",
                    "approximateDeletes","approximateInstantiations","approximateHeight","approximateMemory"
            ));
        }
        if (algos.contains("ilp")) {
            headers.addAll(Arrays.asList(
                    "ilpTime","ilpInstantiationTime","ilpModelTime",
                    "ilpOptimizationTime","ilpDeletionTime",
                    "ilpDeletes","ilpInstantiations","ilpHeight","ilpMemory"
            ));
        }
        if (algos.contains("greedy")) {
            headers.addAll(Arrays.asList(
                    "greedyTime","greedyInstantiationTime","greedyModelTime",
                    "greedyOptimizationTime","greedyDeletionTime",
                    "greedyDeletes","greedyInstantiations","greedyHeight","greedyMemory"
            ));
        }
        System.out.println(String.join(",", headers));
    }

    private static void writeOutput() {
        var algos = ConfigParameter.algorithms;
        ArrayList<String> output = new ArrayList<>();
        output.add(ConfigParameter.ruleFile.replace("rules_", "").replace(".csv", ""));

        // subtract instantiation from model time
        if (algos.contains("optimal")) Utils.optimalTimes[2] -= Utils.optimalTimes[1];
        if (algos.contains("ilp"))     Utils.ilpTimes[2]     -= Utils.ilpTimes[1];

        if (algos.contains("optimal")) {
            for (var t : Utils.optimalTimes)  output.add(getTimeString(t));
            for (var c : Utils.optimalCounts) output.add(String.valueOf(c));
        }
        if (algos.contains("approximate")) {
            for (var t : Utils.approximateTimes)  output.add(getTimeString(t));
            for (var c : Utils.approximateCounts) output.add(String.valueOf(c));
        }
        if (algos.contains("ilp")) {
            for (var t : Utils.ilpTimes)  output.add(getTimeString(t));
            for (var c : Utils.ilpCounts) output.add(String.valueOf(c));
        }
        if (algos.contains("greedy")) {
            for (var t : Utils.greedyTimes)  output.add(getTimeString(t));
            for (var c : Utils.greedyCounts) output.add(String.valueOf(c));
        }

        System.out.println(String.join(",", output));

        Arrays.fill(Utils.optimalTimes,     0L);
        Arrays.fill(Utils.approximateTimes, 0L);
        Arrays.fill(Utils.ilpTimes,         0L);
        Arrays.fill(Utils.greedyTimes,      0L);
        Arrays.fill(Utils.optimalCounts,    0L);
        Arrays.fill(Utils.approximateCounts,0L);
        Arrays.fill(Utils.ilpCounts,        0L);
        Arrays.fill(Utils.greedyCounts,     0L);
    }

    private static String getTimeString(long time) {
        return String.valueOf((long) (time / 1e6));
    }
}