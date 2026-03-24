const fs = require('fs');
const path = require('path');
const { getSession, closeDriver } = require('./db');

async function populateDatabase() {
    const session = await getSession();
    try {
        console.log('Connected to Neo4j. Starting population...');
        
        // Example: Clear existing database (optional)
        // console.log('Clearing existing data...');
        // await session.run('MATCH (n) DETACH DELETE n');
        
        // Example: Read your dataset files here
        // const datasetPath = path.join(__dirname, '../../twitter-dataset/example.csv');
        // const data = fs.readFileSync(datasetPath, 'utf-8');

        // Example insertion query
        /*
        const query = `
            MERGE (p:Person {name: $name})
            RETURN p
        `;
        const params = { name: 'Alice' };
        await session.run(query, params);
        console.log('Inserted Person: Alice');
        */

        console.log('Database population completed successfully.');
    } catch (error) {
        console.error('Error populating database:', error);
    } finally {
        await session.close();
        await closeDriver();
    }
}

populateDatabase();
