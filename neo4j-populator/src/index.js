const fs = require('fs');
const path = require('path');
const csv = require("csv-parser");
const { driver, closeDriver, clearDatabase, createDatabase, setup } = require('./db');

const USE_SAMPLES = process.env.USE_SAMPLE === 'true';

async function ingestCSV(filePath, query, batchSize = 10000) {
    if (!fs.existsSync(filePath)) {
        console.warn(`File not found, skipping: ${filePath}`);
        return;
    }
    
    return new Promise((resolve, reject) => {
        let batch = [];
        let totalProcessed = 0;
        const stream = fs.createReadStream(filePath).pipe(csv());

        stream.on('data', async (row) => {
            batch.push(row);
            if (batch.length >= batchSize) {
                stream.pause();
                const currentBatch = [...batch];
                batch = [];
                try {
                    const session = driver.session({ database: process.env.NEO4J_DATABASE || 'p2e2' });
                    await session.executeWrite(tx => 
                        tx.run(query, { batch: currentBatch })
                    );
                    await session.close();
                    totalProcessed += currentBatch.length;
                    console.log(`Processed ${totalProcessed} rows from ${path.basename(filePath)}`);
                    stream.resume();
                } catch (err) {
                    console.error('Error during batch execution:', err);
                    stream.destroy(err);
                }
            }
        });

        stream.on('end', async () => {
            if (batch.length > 0) {
                try {
                    const session = driver.session({ database: process.env.NEO4J_DATABASE || 'p2e2' });
                    await session.executeWrite(tx => 
                        tx.run(query, { batch })
                    );
                    await session.close();
                    totalProcessed += batch.length;
                    console.log(`Processed ${totalProcessed} rows from ${path.basename(filePath)}`);
                } catch (err) {
                    console.error('Error during final batch execution:', err);
                    return reject(err);
                }
            }
            resolve();
        });

        stream.on('error', reject);
    });
}

async function populateDatabase() {
    try {
        console.log('Connected to Neo4j. Starting population...');

        await createDatabase();
        
        await clearDatabase();
        console.log('Database cleared successfully.');
        
        await setup();
        console.log('Database constraints initialized.');
        
        const datasetDir = path.join(__dirname, '../../twitter-dataset');
        
        const profilePath = path.join(datasetDir, USE_SAMPLES ? 'samples/sample_profile.csv' : 'profile.csv');
        const postsPath = path.join(datasetDir, USE_SAMPLES ? 'samples/sample_posts.csv' : 'posts.csv');
        const profileInsertionPath = path.join(datasetDir, USE_SAMPLES ? 'samples/sample_profile_insertiontime.csv' : 'profile_insertiontime.csv');
        const postsInsertionPath = path.join(datasetDir, USE_SAMPLES ? 'samples/sample_posts_insertiontime.csv' : 'posts_insertiontime.csv');

        const profileQuery = `
            UNWIND $batch AS row
            MERGE (p:Profile {profid: row.profid})
            SET p.profpic = row.profpic,
                p.avglikes = toFloat(row.avglikes),
                p.totallikes = toInteger(row.totallikes),
                p.avgreplies = toFloat(row.avgreplies),
                p.totalreplies = toInteger(row.totalreplies),
                p.avgretweets = toFloat(row.avgretweets),
                p.totalretweets = toInteger(row.totalretweets),
                p.avgquotes = toFloat(row.avgquotes),
                p.totalquotes = toInteger(row.totalquotes),
                p.mostusedhashtag = row.mostusedhashtag,
                p.mostusedhashtagcount = toInteger(row.mostusedhashtagcount),
                p.pscore = toFloat(row.pscore)
        `;
        console.log('--- Ingesting Profiles ---');
        await ingestCSV(profilePath, profileQuery);

        const postsQuery = `
            UNWIND $batch AS row
            MERGE (post:Post {tweetid: row.tweetid})
            SET post.postdata = row.postdata,
                post.username = row.username,
                post.likes = toInteger(row.likes),
                post.retweets = toInteger(row.retweets),
                post.quotes = toInteger(row.quotes),
                post.replies = toInteger(row.replies),
                post.timeposted = row.timeposted
            MERGE (p:Profile {profid: row.profid})
            MERGE (p)-[:POSTED]->(post)
        `;
        console.log('--- Ingesting Posts ---');
        await ingestCSV(postsPath, postsQuery);

        const profileInsertionQuery = `
            UNWIND $batch AS row
            MERGE (i:ProfileInsertion {insertionkey: row.insertionkey})
            SET i.profpic = row.profpic,
                i.avglikes = toInteger(row.avglikes),
                i.totallikes = toInteger(row.totallikes),
                i.avgreplies = toInteger(row.avgreplies),
                i.totalreplies = toInteger(row.totalreplies),
                i.avgretweets = toInteger(row.avgretweets),
                i.totalretweets = toInteger(row.totalretweets),
                i.avgquotes = toInteger(row.avgquotes),
                i.totalquotes = toInteger(row.totalquotes),
                i.mostusedhashtag = row.mostusedhashtag,
                i.mostusedhashtagcount = toInteger(row.mostusedhashtagcount),
                i.pscore = toInteger(row.pscore),
                i.timestamp = toInteger(row.timestamp)
            MERGE (p:Profile {profid: row.insertionkey})
            MERGE (p)-[:INSERTED_AT]->(i)
        `;
        console.log('--- Ingesting Profile Insertion Events ---');
        await ingestCSV(profileInsertionPath, profileInsertionQuery);

        const postsInsertionQuery = `
            UNWIND $batch AS row
            MERGE (i:PostInsertion {insertionkey: row.insertionkey})
            SET i.username = toInteger(row.username),
                i.postdata = toInteger(row.postdata),
                i.likes = toInteger(row.likes),
                i.retweets = toInteger(row.retweets),
                i.quotes = toInteger(row.quotes),
                i.replies = toInteger(row.replies),
                i.timeposted = toInteger(row.timeposted)
            MERGE (post:Post {tweetid: row.insertionkey})
            MERGE (post)-[:INSERTED_AT]->(i)
        `;
        console.log('--- Ingesting Posts Insertion Events ---');
        await ingestCSV(postsInsertionPath, postsInsertionQuery);

        console.log('Database population completed successfully.');
    } catch (error) {
        console.error('Error populating database:', error);
    } finally {
        await closeDriver();
    }
}

populateDatabase();
