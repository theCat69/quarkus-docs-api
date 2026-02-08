# TODOS before 1.0.0

## Code good practice

- look for a POJO example in the code => Write this example in AGENTS.md for agents to follow code guidelines.
- do the same for store, services and resources

## swagger & swagger-ui 

- add documentation for all endpoints to help AI to discover them with the openapi definition.
- include Body example in the openapi definition if possible.

## Use sqllite instead for indexes storage ? (faster retrival)

- Change from file based indexes to sqllite database indexes. Probably faster to retrieve and search inside.

## Create absraction for everything that is linked to Asciidoc

- Currently we only parse and download asciidoc, in the future we will support other types of files (markdown for example). 
- Create an abstraction so it is easy to have another file type for another repository like quarkiverse.

## Find sections by api

- We have two search endpoints right now that returns file name and sections.
- Add an endpoints to return sections content.

## Code samples

- Extract ALL code samples when caching a new version
- Index them with corresponding sections keywords + add indexes base on "imports". If in the file we have "import jakarta.ws.rs.GET" => 
word : "jakarta.ws.rs.GET" | score : 5 (boost imports by 5)

## Find code samples by API

- Search code samples directly with keywords. (like search for sections but return code samples content). 
