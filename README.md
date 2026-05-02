# Dependency-aware-meaningful-erasure

To run this project, you need to set up three main components: a Neo4j database, the Node.js data populator, and the Java application.

## 1. Start a Neo4j Database
You need a running Neo4j instance (Neo4j Desktop or a Docker container).
* Make sure it is running.
* Keep your connection URI (e.g., `neo4j://localhost:7687`), username, and password handy.

## 2. Populate the Database (Node.js)
The `neo4j-populator` directory contains a Node script to initialize the graph with the Twitter dataset.
1. Open a terminal and navigate to the `neo4j-populator` folder.
2. Install dependencies by running:
   ```bash
   npm install
   ```
3. Create a file named `.env` in the `neo4j-populator` folder and add your Neo4j credentials:
   ```env
   NEO4J_URI=neo4j://127.0.0.1:7687
   NEO4J_USERNAME=neo4j
   NEO4J_PASSWORD=your_password
   USE_SAMPLE=true
   ```
4. Run the populator script:
   ```bash
   npm run db:populate
   ```

## 3. Setup Gurobi Optimizer
The Java application (`graph-p2e2-erasure`) uses the **Gurobi Optimization Solver**.
* You **must** have Gurobi installed on your system.
* You need a valid Gurobi license file (usually `gurobi.lic` set up via the `GRB_LICENSE_FILE` environment variable). Without this, the Java application will crash when it tries to initialize the environment.

## 4. Configure the Java Application
1. Navigate into the `graph-p2e2-erasure` directory.
2. Create a `config.json` file. This file needs to provide Neo4j credentials and paths to the rules and schema files. Here is an example structure:
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
   *(Note: You will need to make sure the CSV files like `rules.csv` and `schema.csv` exist at the `configPath` you specify).*

## 5. Build and Run the Java Application
Once Gurobi is set up and your `config.json` is ready:
1. Compile the project using Maven in the `graph-p2e2-erasure` directory:
   ```bash
   mvn clean compile
   ```
2. Run the main class. You can pass the path to your config file as an argument:
   ```bash
   mvn exec:java -Dexec.mainClass="org.example.Main" -Dexec.args="config.json"
   ```