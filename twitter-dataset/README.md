# Twitter Dataset

This directory contains the data used to populate the Neo4j database for this project.

## Download Instructions

The full dataset is large (>200MB) and is stored externally. You can download the required CSV files from the following link:
**[Download Dataset](https://my.hidrive.com/lnk/eshsTVV5I)**

Once downloaded, extract the following files directly into this `twitter-dataset` directory:
- `posts.csv`
- `profile.csv`
- `posts_insertiontime.csv`
- `profile_insertiontime.csv`

## Dataset Schema

The data models a Twitter-like social network and its temporal insertion events.

### Nodes
- **Profile:** Represents a user account (Key properties: `profid`, metrics like `avglikes`, `totallikes`, etc.)
- **Post:** Represents a tweet/post (Key properties: `tweetid`, `postdata`, `likes`, `retweets`, etc.)
- **InsertionEvent:** Captures temporal ingestion and versioning (Key properties: `insertionkey`, `timestamp`)

### Relationships
- `(:Profile)-[:POSTED]->(:Post)`: Maps which profile created which post.
- `(:Post)-[:INSERTED_AT]->(:InsertionEvent)`: Tracks when a post was ingested.
- `(:Profile)-[:UPDATED_AT]->(:InsertionEvent)`: Tracks profile metric updates over time.

## Sample Data (`samples/`)

Because parsing the entire multi-GB dataset takes significant time and memory, this folder also contains a `samples/` directory with a miniature version of the database (currently 2 randomly selected profiles and all their associated posts and insertion events).

These samples maintain all referential integrity thanks to the local extraction script. 

### Generating New Samples
If you want to generate a new sample subset from the main CSV files, ensure you have Python and `pandas` installed, then run:

```bash
python sample_extraction.py
```
This will randomly select profiles, extract all their associated posts, and map their insertion timelines seamlessly.
