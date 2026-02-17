-- liquibase formatted sql
-- changeset quarkus-docs-api:003-drop-legacy-tables

DROP TABLE IF EXISTS code_sample_keywords;
DROP TABLE IF EXISTS code_samples;
DROP TABLE IF EXISTS section_keywords;
DROP TABLE IF EXISTS sections;
DROP TABLE IF EXISTS file_keywords;
DROP TABLE IF EXISTS document_metadata;
DROP TABLE IF EXISTS files;
