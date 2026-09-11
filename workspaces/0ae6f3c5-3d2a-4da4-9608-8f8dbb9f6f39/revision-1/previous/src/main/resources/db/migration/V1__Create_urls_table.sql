-- V1 Create urls table
CREATE TABLE IF NOT EXISTS urls (
    short_code VARCHAR(8) PRIMARY KEY,
    original_url TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);
