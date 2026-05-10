# Neo4j Database Populator

This directory contains a Node.js script designed to programmatically initialize and populate the Neo4j database with the Twitter temporal dataset.

> **⚠️ IMPORTANT: For the Full Dataset**
> This script uses a Node.js `fs` ReadStream coupled with `csv-parser` to stream data batches into Neo4j. While highly reliable, parsing the full ~700MB dataset through Node.js can take a significant amount of time. 
> 
> **For populating the full dataset, it is highly recommended to use the native "Data Importer" built into Neo4j Desktop**, using the Cypher bulk importer scripts, as it is exponentially faster. 
> 
> This Node script is primarily intended for rapidly populating the **miniature sample datasets** (`samples/`) for testing, development, and workflow validation.

## Setup Instructions

### 1. Install Dependencies
Make sure you are in the `neo4j-populator` directory and install the necessary Node modules:
```bash
npm install
```

### 2. Configure Environment Variables
Create a `.env` file in the root of the `neo4j-populator` directory (next to `package.json`) and configure your database credentials and target dataset. 

Here is an example `.env` file configuration:
```env
# Your Neo4j Database Connection
NEO4J_URI=neo4j://127.0.0.1:7687
NEO4J_USERNAME=neo4j
NEO4J_PASSWORD=your_secure_password
NEO4J_DATABASE=neo4j

# Set to 'true' to import the miniature data from the 'samples/' folder.
# Set to 'false' to target the main dataset CSV files.
USE_SAMPLE=true
```

## Running the Importer

Once your `.env` is configured and your Neo4j database is running natively in the background, you can start the population script:

```bash
npm run db:populate
```

The script will automatically:
1. Connect to your database.
2. Clear the existing nodes and relationships securely.
3. Initialize the necessary schema constraints (e.g., uniqueness on `profid` and `tweetid`).
4. Read your CSV files sequentially and stream batch constraints into Neo4j.



For executing the Java Application,

on intellij: (better use IDE specific buttons than command line execution)
more actions -> Edit Configurations 
    Set Main Class to: org.example.Main
    Set Program Arguments to: config.json
    Set working directory to: D:\Database Internals Project\Graph-P2E2-Erasure\dependency-aware-meaningful-erasure\graph-p2e2-erasure

Then use the Run button.