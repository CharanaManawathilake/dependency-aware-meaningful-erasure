# Dependency-Aware Meaningful Erasure

This repository contains a research and prototype implementation for dependency-aware meaningful erasure over a Neo4j graph, using a Twitter-like temporal dataset. The project explores how to remove or revise data while respecting dependency constraints and preserving graph consistency.

The workspace includes multiple related components:

-   `graph-p2e2-erasure`: the main Java-based erasure engine
-   `neo4j-p2e2`: a Spring Boot-based Neo4j application
-   `neo4j-populator`: Node.js utility to load graph data into Neo4j
-   `twitter-dataset`: full and sample CSV inputs used for the experiments
-   `D-ME Performance optimized (Schema Altered)`: a schema-altered, performance-oriented variant of the system

---

## Repository layout

```text
dependency-aware-meaningful-erasure/
├── README.md
├── LICENSE
├── graph-p2e2-erasure/
│   ├── pom.xml
│   ├── config.json.example
│   └── src/
├── neo4j-p2e2/
│   ├── pom.xml
│   └── src/
├── neo4j-populator/
│   ├── package.json
│   ├── README.md
│   └── src/
├── twitter-dataset/
│   ├── README.md
│   ├── samples/
│   └── ... CSV files
├── D-ME Performance optimized (Schema Altered)/
│   ├── README.md
│   ├── graph-p2e2-erasure/
│   └── neo4j-populator/
└── LICENSE
```

---

## Project goal

The system models a graph database where facts, properties, and temporal insertion events are connected by dependency rules. The erasure engine computes which data should be removed or recomputed so that the resulting graph is consistent with the dependency-aware constraints.

This is used for research on:

-   meaningful data erasure
-   dependency-aware deletion
-   temporal graph consistency
-   graph rule-driven reasoning
-   optimization-based deletion strategies

---

## Main components

### 1. `graph-p2e2-erasure`

This is the core Java implementation of the erasure engine.

Key characteristics:

-   reads dependency rules and schema metadata from CSV files
-   parses rules and graph dependencies from configuration
-   uses Neo4j for data storage
-   uses Gurobi optimization for deletion and scheduling logic
-   supports both user-initiated deletion and retention-driven scheduling

Relevant files:

-   `graph-p2e2-erasure/pom.xml`
-   `graph-p2e2-erasure/config.json.example`
-   `graph-p2e2-erasure/src/main/java/org/example/Main.java`

### 2. `neo4j-p2e2`

This is a separate Spring Boot project that exposes the Neo4j-based application layer and configuration. It includes a sample application configuration in `src/main/resources/application.yml.example`.

### 3. `neo4j-populator`

This Node.js utility initializes a Neo4j database with the Twitter dataset.

It can:

-   connect to Neo4j
-   clear existing graph data
-   apply constraints
-   bulk-load dataset records from CSVs
-   support sample or full data imports

### 4. `twitter-dataset`

This directory contains the social graph CSV data used for experimentation.

The full dataset is large and is meant to be downloaded externally. The project also includes a sample subset to enable local testing and quick verification.

### 5. `D-ME Performance optimized (Schema Altered)`

This directory contains a performance-oriented variant of the project where the database schema stores time data as properties on nodes instead of separate nodes. The README in that folder explains the exact setup and execution steps for that altered schema version.

---

## Prerequisites

Before running the project, ensure the following are available:

-   Java 21+
-   Maven
-   Node.js and npm
-   Neo4j instance (local or remote)
-   Gurobi Optimizer installation with valid license
-   A configured dataset and dependency files

---

## Quick start

### 1. Start Neo4j

Make sure a Neo4j database is running and reachable.

Example URI:

```text
neo4j://127.0.0.1:7687
```

### 2. Populate the database

From the `neo4j-populator` directory:

```bash
npm install
```

Create a `.env` file:

```env
NEO4J_URI=neo4j://127.0.0.1:7687
NEO4J_USERNAME=neo4j
NEO4J_PASSWORD=your_password
NEO4J_DATABASE=neo4j
USE_SAMPLE=true
```

Then run:

```bash
npm run db:populate
```

> The project also contains a sample dataset for development and validation, and the repository includes instructions for generating or loading those inputs.

### 3. Install Gurobi

The core erasure engine depends on Gurobi.

You must:

-   install the Gurobi Optimizer
-   configure a valid license file
-   ensure the Java process can access it at runtime

The main application initializes Gurobi in `Main.java`, so missing or invalid Gurobi configuration will break the run.

### 4. Prepare config for Java erasure engine

The main engine expects a JSON configuration file similar to the example in `graph-p2e2-erasure/config.json.example`.

Example:

```json
{
    "connectionUrl": "neo4j://localhost:7687",
    "database": "neo4j",
    "username": "neo4j",
    "password": "your_password",
    "configPath": "./",
    "ruleFile": "rules.csv",
    "schemaFile": "schema.csv",
    "derivedFile": "derived.csv",
    "numKeys": 100,
    "insertionTimeRelationship": "YOUR_REL_NAME"
}
```

The exact CSV files and paths depend on the dataset and rule files being used in your experiment.

### 5. Build and run the Java engine

From `graph-p2e2-erasure`:

```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="org.example.Main" -Dexec.args="config.json"
```

Alternatively, you can pass a different config file as the first command-line argument when running the application.

---

## Dataset and sample input

The repository expects graph input rules and schema definitions as CSV files, typically in a resource directory such as:

-   `graph-p2e2-erasure/src/main/resources/graph_dependency_rules/`
-   `graph-p2e2-erasure/src/main/resources/relational_dependency_rules/`

The `twitter-dataset` directory contains the data used to populate the graph, including:

-   `posts.csv`
-   `profile.csv`
-   `posts_insertiontime.csv`
-   `profile_insertiontime.csv`
-   sample files under `samples/`

The sample extraction utility can generate smaller subsets for testing.

---

## Notes and caveats

-   This project is research-oriented and configuration-heavy.
-   A full dataset import can be large and time-consuming.
-   The Java engine depends on both Neo4j and Gurobi being properly configured.
-   The schema-altered variant under `D-ME Performance optimized (Schema Altered)` is a specialized version and may differ from the main default setup.

---

## License

This project is distributed under the MIT-style license included in the repository. See `LICENSE` for details.

---

## Recommended workflow

1. Start Neo4j.
2. Load the graph using `neo4j-populator`.
3. Download or generate the Twitter dataset inputs.
4. Install and license Gurobi.
5. Configure the Java JSON file for your dataset.
6. Run the `graph-p2e2-erasure` engine.
7. Compare results with the performance-optimized schema-altered version if needed.

---

## Additional references

-   `D-ME Performance optimized (Schema Altered)/README.md`
-   `neo4j-populator/README.md`
-   `twitter-dataset/README.md`

These files contain more detailed setup guidance for the specific subcomponents of the repository.
