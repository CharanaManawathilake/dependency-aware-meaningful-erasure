const neo4j = require('neo4j-driver');
require('dotenv').config();

const uri = process.env.NEO4J_URI || 'bolt://localhost:7687';
const user = process.env.NEO4J_USERNAME || 'neo4j';
const password = process.env.NEO4J_PASSWORD || 'password';

const driver = neo4j.driver(uri, neo4j.auth.basic(user, password));

async function getSession() {
    return driver.session();
}

async function closeDriver() {
    await driver.close();
}

module.exports = {
    driver,
    getSession,
    closeDriver
};
