2️⃣ Generate the tsvector

UPDATE doc_chunks
SET content_tsv =
setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
setweight(to_tsvector('english', coalesce(section, '')), 'A') ||
setweight(to_tsvector('english', coalesce(summary, '')), 'B') ||
setweight(to_tsvector('english', content), 'C');

For example this boosts:
title + section highest

summary next
body content last

Perfect for agent relevance ranking.

3️⃣ Insert Example (One Chunk)
```sql
INSERT INTO doc_chunks (
    id, page, title, section, url, topics, extensions, summary, content, content_tsv
) VALUES (
             'mailer-reactive-usage',
             'sending-emails-using-smtp.adoc',
             'Sending emails using SMTP',
             'Using the reactive mailer',
             'https://quarkus.io/guides/sending-emails-using-smtp#using-the-reactive-mailer',
             ARRAY['mail','mailer','reactive'],
             ARRAY['io.quarkus:quarkus-mailer'],
             'Explains how to send emails using the reactive mailer API with Mutiny in Quarkus.',
             'The reactive mailer uses Mutiny reactive types... @GET @Path("/reactive") ...',
             setweight(to_tsvector('english', 'Sending emails using SMTP'), 'A') ||
             setweight(to_tsvector('english', 'Using the reactive mailer'), 'A') ||
             setweight(to_tsvector('english', 'Explains how to send emails using the reactive mailer API with Mutiny in Quarkus.'), 'B') ||
             setweight(to_tsvector('english', 'The reactive mailer uses Mutiny reactive types...'), 'C')
         );
```

4️⃣ Core Search Query (Agent-Friendly)
🔎 Keyword Search with Ranking
```sql
SELECT
    id,
    page,
    title,
    section,
    url,
    summary,
    extensions,
    topics,
    ts_rank(content_tsv, plainto_tsquery(:q)) AS score
FROM doc_chunks
WHERE content_tsv @@ plainto_tsquery(:q)
ORDER BY score DESC
    LIMIT :limit;
```

5️⃣ Search with Metadata Boost (Extensions / Topics)

If the agent passes extension info:
```sql
SELECT
    id,
    page,
    title,
    section,
    url,
    summary,
    extensions,
    topics,
    ts_rank(content_tsv, plainto_tsquery(:q)) +
    CASE WHEN extensions @> ARRAY[:extension] THEN 0.5 ELSE 0 END AS score
FROM doc_chunks
WHERE content_tsv @@ plainto_tsquery(:q)
ORDER BY score DESC
LIMIT :limit;
```

Example:
-- :q = 'reactive mailer blocking'
-- :extension = 'io.quarkus:quarkus-mailer'

6️⃣ Fallback Fuzzy Search (When FTS Misses)

This helps for typos or API names:
```sql
SELECT
    id,
    page,
    title,
    section,
    url,
    summary,
    similarity(content, :q) AS score
FROM doc_chunks
ORDER BY score DESC
    LIMIT :limit;
```


You can combine it:
```sql
SELECT *,
       ts_rank(content_tsv, plainto_tsquery(:q)) +
       similarity(content, :q) * 0.2 AS score
FROM doc_chunks
WHERE content_tsv @@ plainto_tsquery(:q)
   OR content % :q
ORDER BY score DESC
    LIMIT :limit;
```

7️⃣ Agent-Friendly API Response Shape

Return this JSON:
```sql
{
"results": [
    {
        "id": "mailer-reactive-usage",
        "page": "sending-emails-using-smtp.adoc",
        "title": "Sending emails using SMTP",
        "section": "Using the reactive mailer",
        "summary": "Explains how to send emails using the reactive mailer API with Mutiny in Quarkus.",
        "extensions": ["io.quarkus:quarkus-mailer"],
        "topics": ["mail", "mailer", "reactive"],
        "score": 0.82,
        "url": "https://quarkus.io/guides/sending-emails-using-smtp#using-the-reactive-mailer"
    }
]
}
```


This lets the agent:
- decide relevance
- cite sources
- fetch full content if needed