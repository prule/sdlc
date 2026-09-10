-- Adds the people + credits schema for the catalog/movies credits sub-resource (UC-003).
CREATE TABLE people (
    id   UUID PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE credits (
    id            UUID PRIMARY KEY,
    movie_id      UUID NOT NULL REFERENCES movies (id) ON DELETE CASCADE,
    person_id     UUID NOT NULL REFERENCES people (id),
    kind          VARCHAR(4) NOT NULL,          -- 'CAST' | 'CREW'
    -- cast-only (NULL for crew):
    character     TEXT NULL,                     -- optional even for cast
    billing_order INT NULL,
    -- crew-only (NULL for cast):
    department    TEXT NULL,
    job           TEXT NULL,
    CONSTRAINT credits_shape CHECK (
        (kind = 'CAST' AND billing_order IS NOT NULL AND department IS NULL AND job IS NULL)
        OR (kind = 'CREW' AND department IS NOT NULL AND job IS NOT NULL AND billing_order IS NULL AND character IS NULL)
    )
);

CREATE INDEX idx_credits_movie ON credits (movie_id);
