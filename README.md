# dependency-aware-meaningful-erasure

A benchmarking and comparison framework for **minimal consistent erasure** in Neo4j property graphs. When a property value becomes invalid or must be deleted, this system finds the minimum set of dependent derived values that must also be erased to restore consistency — and compares four algorithms doing so.

---

## Table of Contents

- [Overview](#overview)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Dependencies](#dependencies)
- [Configuration](#configuration)
- [CSV Rule Files](#csv-rule-files)
- [Algorithms](#algorithms)
- [Running the Project](#running-the-project)
- [Output Format](#output-format)
- [Neo4j Data Model](#neo4j-data-model)
- [Adding a New Dataset](#adding-a-new-dataset)
- [Known Limitations](#known-limitations)

---

## Overview

In a property graph, many values are **derived** from others via dependency rules. For example:

```
Product.totalsales  ←  Order.quantity
Supplier.totalrevenue  ←  Product.totalsales  ←  Order.quantity
```

When a base value must be erased — due to data corruption, GDPR compliance, fraud detection, or sensor faults — every downstream derived value that depended on it becomes inconsistent and must also be erased. This is the **cascading erasure problem**.

Naively deleting everything downstream is safe but wasteful. This system finds the **minimum hitting set** of the dependency hypergraph — the smallest set of cells whose deletion restores consistency — and benchmarks four algorithms solving this NP-hard problem.

### Real World Applications

- **Healthcare** — erasing lab results that corrupted downstream risk scores and treatment decisions
- **Finance** — removing fraud-contaminated derived credit scores and regulatory capital calculations
- **Social Media** — purging bot-influenced engagement metrics from reputation and trending systems
- **Industrial Safety** — correcting faulty sensor readings that cascaded into plant-wide safety metrics

---

## Architecture

```
config.json
    │
    ▼
Main.java
    ├── parseRules()         ← rules_<dataset>.csv
    ├── parseSchema()        ← schema_<dataset>.csv
    ├── parseDerivedData()   ← derived_<dataset>.csv
    ├── checkNonCyclicRules()
    └── iterateProperties()
            │
            ▼
        Instantiator.java
            ├── getKeys()              ← sample random cells from Neo4j
            ├── completePropVal()      ← fetch value + insertion time
            ├── instantiateAttachedCells()  ← BFS expansion via rules
            └── deleteCells() / resetValues()
            │
            ▼
        InstantiatedModel.java
            └── BFS hypergraph construction
                    │
                    ▼
            Four deletion algorithms (Main.java)
                ├── optimalDelete()         ← bottom-up DP
                ├── approximateDelete()     ← greedy by children count
                ├── ilpApproach()           ← Gurobi MIP
                └── greedySetCoverDelete()  ← greedy set cover (ln(n) guarantee)
                    │
                    ▼
            Utils.java  ← timing, count, memory metrics
                    │
                    ▼
            CSV output to stdout
```

---

## Project Structure

```
src/
└── main/
    ├── java/org/example/
    │   ├── Main.java                  # Entry point, algorithm orchestration, I/O
    │   ├── ConfigParameter.java       # All configuration parameters
    │   ├── Instantiator.java          # Neo4j queries, hypergraph expansion
    │   ├── InstantiatedModel.java     # BFS hypergraph construction
    │   ├── Utils.java                 # Shared metrics arrays
    │   └── GraphDependencyRules/
    │       ├── Cell.java              # (property, key, value) triple + HyperEdge
    │       ├── Property.java          # (node, property) pair
    │       └── Rule.java              # Parsed dependency rule
    └── resources/
        └── graph_dependency_rules/
            ├── rules_<dataset>.csv    # Dependency rules
            ├── schema_<dataset>.csv   # Node key mappings
            └── derived_<dataset>.csv  # Derived property declarations
config.json                            # Runtime configuration
```

---

## Dependencies

| Dependency | Purpose |
|---|---|
| Neo4j Java Driver | Graph database connectivity |
| Gurobi | ILP solver for exact minimum hitting set |
| Apache Commons CSV | Parsing rule/schema CSV files |
| org.json | Parsing config.json |

Gurobi requires a valid license. Academic licenses are available free at [gurobi.com/academia](https://www.gurobi.com/academia/academic-program-and-licenses/).

---

## Configuration

All runtime parameters are controlled via `config.json`. Multiple datasets can be run in a single execution using the `cases` array.

```json
{
  "cases": [
    {
      "enabled": true,
      "algorithms": ["optimal", "approximate", "ilp", "greedy"],
      "connectionUrl": "neo4j://localhost:7687",
      "database": "my-database",
      "username": "neo4j",
      "password": "password",
      "configPath": ".\\src\\main\\resources\\graph_dependency_rules\\",
      "ruleFile": "rules_twitter.csv",
      "schemaFile": "schema_twitter.csv",
      "derivedFile": "derived_twitter.csv",
      "numKeys": 100,
      "measureMemory": true,
      "insertionTimeRelationship": "INSERTED_AT",
      "timeoutMs": 30000,
      "propertyNumKeys": {
        "pscore": 10
      },
      "propertyAlgorithms": {
        "pscore": ["optimal", "approximate"]
      }
    }
  ]
}
```

### Configuration Parameters

| Parameter | Type | Default | Description |
|---|---|---|---|
| `enabled` | boolean | `true` | Whether to run this case |
| `algorithms` | array | all four | Which algorithms to run: `"optimal"`, `"approximate"`, `"ilp"`, `"greedy"` |
| `connectionUrl` | string | — | Neo4j connection URL |
| `database` | string | — | Neo4j database name |
| `username` | string | — | Neo4j username |
| `password` | string | — | Neo4j password |
| `configPath` | string | — | Path to CSV rule files directory |
| `ruleFile` | string | — | Dependency rules CSV filename |
| `schemaFile` | string | — | Schema CSV filename |
| `derivedFile` | string | — | Derived properties CSV filename |
| `numKeys` | int | `10` | Number of random keys to sample per property |
| `measureMemory` | boolean | `true` | Whether to measure memory usage per algorithm |
| `insertionTimeRelationship` | string | `"INSERTED_AT"` | Neo4j relationship type for insertion timestamps |
| `timeoutMs` | long | unlimited | Per-property timeout in milliseconds |
| `propertyNumKeys` | object | — | Override `numKeys` for specific properties |
| `propertyAlgorithms` | object | — | Override `algorithms` for specific properties |

---

## CSV Rule Files

### `schema_<dataset>.csv`
Maps node labels to their key properties.

```
Profile,profid
Post,tweetid
```

### `rules_<dataset>.csv`
Defines dependency rules in Cypher-like format. Each row is one rule where the first RETURN column is the **head** (derived property) and remaining columns are the **tail** (source properties).

```
MATCH,(p1:Profile)-[:POSTED]->(p2:Post),RETURN,p1.totallikes,p2.likes
MATCH,(p1:Profile),RETURN,p1.pscore,p1.avgreplies,p1.avgretweets
```

Format: `MATCH,<cypher_pattern>,RETURN,<head_property>,<tail_property_1>,...`

### `derived_<dataset>.csv`
Declares which properties are derived (not base). Same format as rules. Properties listed here are excluded from the set of deletion starting points.

```
MATCH,(p1:Profile)-[:POSTED]->(p2:Post),RETURN,p1.totallikes,p2.likes
```

---

## Algorithms

### Optimal (Bottom-up DP)
Exact minimum deletion set. Traverses `treeLevels` bottom-up assigning costs, then greedily selects `minCell` per edge top-down.

- **Time:** O(n × b × k)
- **Space:** O(n + e)
- **Guarantee:** Exact — on tree-structured hypergraphs

### Approximate (Greedy by children count)
Picks the cell with the fewest outgoing edges at each BFS step. Fast but no theoretical quality guarantee.

- **Time:** O(n × b²× k)
- **Space:** O(n + e)
- **Guarantee:** None

### ILP (Gurobi MIP)
Formulates minimum hitting set as a binary integer program. One variable per cell, one constraint per hyperedge. Solved by Gurobi's branch-and-bound.

- **Time:** O(2^n) worst case, polynomial in practice on sparse graphs
- **Space:** O(n × k)
- **Guarantee:** Exact — on general hypergraphs

### Greedy Set Cover
Builds an inverse index (cell → edges it covers), then iteratively picks the cell covering the most uncovered edges. Uses a lazy priority queue for efficiency.

- **Time:** O(e × log n) with priority queue
- **Space:** O(n + e)
- **Guarantee:** ln(n) approximation — best possible unless P=NP

---

## Running the Project

### Prerequisites

1. **Neo4j** running and accessible at the configured URL
2. **Gurobi** installed with a valid license
3. **Java 11+**
4. **Maven** or your preferred build tool

### Neo4j Data Requirements

Each node must have an `INSERTED_AT` relationship to a timestamp node holding property-level insertion times:

```cypher
(Profile {profid: 1, totallikes: 500})
    -[:INSERTED_AT]->
({totallikes: 1620000000})
```

### Build and Run

```bash
mvn clean package
java -jar target/your-jar.jar config.json
```

Or pass config path explicitly:

```bash
java -jar target/your-jar.jar path/to/config.json
```

Output is written to **stdout** as CSV. Redirect to a file:

```bash
java -jar target/your-jar.jar config.json > results.csv
```

---

## Output Format

One header row followed by one row per property per dataset case.

```
Dataset,Attribute,optimalTime,optimalInstantiationTime,optimalModelTime,
optimalOptimizationTime,optimalDeletionTime,optimalDeletes,optimalInstantiations,
optimalHeight,optimalMemory,...
```

Only columns for **enabled algorithms** are emitted.

### Column Descriptions

| Column | Description |
|---|---|
| `Dataset` | Dataset name derived from rule filename |
| `Attribute` | Property being deleted (e.g. `Post likes`) |
| `*Time` | Total wall time in milliseconds |
| `*InstantiationTime` | Time spent on Neo4j queries during BFS |
| `*ModelTime` | Time spent constructing the hypergraph (excluding instantiation) |
| `*OptimizationTime` | Time spent on the deletion algorithm itself |
| `*DeletionTime` | Time spent executing Neo4j deletions |
| `*Deletes` | Number of additional cells deleted beyond the root |
| `*Instantiations` | Number of cells instantiated during BFS |
| `*Height` | Depth of the dependency tree |
| `*Memory` | Estimated memory usage in bytes |

---

## Neo4j Data Model

### Required Structure

```
(NodeA {keyProp: id, derivedProp: value})
    -[:INSERTED_AT]->
(TimestampNode {derivedProp: insertionTime})
```

The `INSERTED_AT` relationship connects every node to a timestamp node that holds the insertion time for each of its properties. This drives the temporal filtering in `queryRule()` — only cells inserted at or after the source cell's insertion time are considered dependent.

### Example — Twitter Dataset

```cypher
(:Profile {profid: 1, totallikes: 1500, pscore: 0.87})
    -[:INSERTED_AT]->
({totallikes: 1620000000, pscore: 1620000001})

(:Post {tweetid: 42, likes: 300, username: "user1"})
    -[:INSERTED_AT]->
({likes: 1620000000})

(:Profile)-[:POSTED]->(:Post)
```

---

## Adding a New Dataset

1. **Create three CSV files** in `configPath`:
   - `schema_<name>.csv` — node label to key property mappings
   - `rules_<name>.csv` — dependency rules
   - `derived_<name>.csv` — derived property declarations

2. **Populate Neo4j** with nodes, relationships, and `INSERTED_AT` timestamp nodes

3. **Add a case to `config.json`**:
```json
{
  "enabled": true,
  "ruleFile": "rules_<name>.csv",
  "schemaFile": "schema_<name>.csv",
  "derivedFile": "derived_<name>.csv",
  "database": "your-database"
}
```

No Java code changes required.

---

## Known Limitations

| Limitation | Detail |
|---|---|
| `resetValues()` unimplemented | Deleted values are not restored after each key — results after the first key per property may be operating on already-modified data |
| `checkNonCyclicRules()` uses `assert` | Cycle detection can be silently bypassed if JVM assertions are disabled. Run with `-ea` flag to enable |
| ILP requires Gurobi license | Commercial license required for production use; free academic licenses available |
| `WHERE` uses `OR` across tail conditions | Records are returned if any insertion time condition is met, not all — verify this matches intended semantics for your dataset |
| Greedy without priority queue | Default greedy implementation is O(n × e) — slow on large hypergraphs. Priority queue fix recommended for datasets with `numKeys` > 50 |
| `ilpHeight` always 0 | `Utils.ilpCounts[2]` is never assigned in the ILP algorithm — height metric missing for ILP |
| Single Neo4j database per case | All node types in a case must exist in the same database |
