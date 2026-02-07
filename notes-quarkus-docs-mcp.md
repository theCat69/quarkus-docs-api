# Quarkus docs MCP

## Goal

Give ai agent access to up to date documentation specifically for quarkus.
Give ai agent the possibility to parse those files efficiently

## Architecture

- local STDIO mcp server in typescript
- remote or local Quarkus HTTP server in a docker container

## How it works: Basics

### First call index 

- AI Agent call STDIO mcp server index documentation for version
- MCP server calls quarkus api
- If index for version is in cache quarkus api returns it. Otherwise it downloads it using https://api.github.com/repos/quarkusio/quarkus/contents/docs/src/main/asciidoc?ref=<quarkus_version>, format it for AI, save it in cache and returns it.

### Second call doc

- AI Agent call STDIO mcp server for specific documentation with version
- MCP server calls quarkus api 
- If documentation is already cached for version it returns it. Otherwise it downloads this specific documentation using https://api.github.com/repos/quarkusio/quarkus/contents/<file_path>?ref=<quarkus_version>. In parallel, in background, the server will download the full version with https://www.github.com/quarkusio/quarkus/archive/refs/heads/<quarkus_version>.zip extract the documentation and put it on disc for caching. 

### Advances search in cached documentation

- AI agent call STDIO mcp server with keywords and quarkus version.
- Lazyly download full version in cache and index it.
- First we look into keyword index and find most relevant files (5 or 10).
- We send the list of files with indexed section and relevant keywords for those sections.
- Each result will have a score attached to it.
- AI will do another query to get the relevant section.

### Advanced section search

- After searching files with keywords AI can do another round of calls to get relevant sections.
- AI agent call STDIO mcp server with keywords and quarkus version and filePaths.
- We send back the most relevant sections with scores attached to it (3 to 5 sections).

### Keywords match algorithm

- get keywords indiviually.
- parse index to get every match with score.
- add score from all keywords. If there is more than one keyword match boost this by a multiplicative or additional factor.
- order by score.
- get top x result.

## Background tasks

### Cache freshness

To ensure cache stays fresh every X hours the server will download all indexes for all cached quarkus versions using https://api.github.com/repos/quarkusio/quarkus/contents/docs/src/main/asciidoc?ref=<quarkus_version>.
Then it compares all sha to check for changed files. 
If file changed it downloads the new version using https://api.github.com/repos/quarkusio/quarkus/contents/<file_path>?ref=<quarkus_version> and replace each of them in the cached folder. 
Then it replaces the index by the new one.

### Keywords index

For all keywords index we need to ignore code sections.
Eveytime an index is replaced a background task should create a keyword index and put it in cache.
This keyword index should : 
- have the file path
- have a list of keywords with a score. Score is determine by the number of occurence of the word.
- the file name should be boosted in score by 10
- contains sections index with start and end line number. A list of keywords for this section with score. Boost section title score.

## Cache files folder structure

.cache > quarkus_version > docs => all docs and subfolders
.cache > quarkus_version > file_index.json => all files with path and sha (from github)
.cache > quarkus_version > keyword_index.json => all files with most relevant keywords 

## And then ?

### Quarkiverse support

TODO

