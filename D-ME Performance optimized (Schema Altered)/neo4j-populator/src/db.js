const neo4j = require('neo4j-driver');
require('dotenv').config();

const uri = process.env.NEO4J_URI || 'neo4j://127.0.0.1:7687';
const user = process.env.NEO4J_USERNAME || 'neo4j';
const password = process.env.NEO4J_PASSWORD || 'password';
const database = process.env.NEO4J_DATABASE || 'neo4j';

const driver = neo4j.driver(uri, neo4j.auth.basic(user, password));

async function getSession() {
    return driver.session({ database });
}

async function closeDriver() {
    await driver.close();
}

async function createDatabase() {
    try {
        console.log(`Attempting to create database '${database}'...`);
        await driver.executeQuery(
            `CREATE DATABASE \`${database}\` IF NOT EXISTS`, 
            {}, 
            { database: 'system' }
        );
        console.log(`Database '${database}' created or already exists.`);
    } catch (error) {
        console.error('Failed to create database:', error.message);
    }
}

async function clearDatabase() {
  await driver.executeQuery(`
    MATCH (n)
    DETACH DELETE n
  `, {}, { database });
}

async function setup() {
  await driver.executeQuery(`
    CREATE CONSTRAINT post_id IF NOT EXISTS
    FOR (p:Post) REQUIRE p.tweetid IS UNIQUE
  `, {}, { database });

  await driver.executeQuery(`
    CREATE CONSTRAINT profile_id IF NOT EXISTS
    FOR (u:Profile) REQUIRE u.profid IS UNIQUE
  `, {}, { database });

}

module.exports = {
    driver,
    getSession,
    closeDriver,
    clearDatabase,
    createDatabase,
    setup
};
