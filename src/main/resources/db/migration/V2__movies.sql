-- Adds the movies + movie_genres schema for the catalog/movies capability (UC-001).
CREATE TABLE movies (
    id              UUID PRIMARY KEY,
    title           TEXT NOT NULL,
    release_year    INT NOT NULL,
    runtime_minutes INT NULL,
    synopsis        TEXT NULL,
    rating          NUMERIC(2, 1) NULL CHECK (rating >= 0 AND rating <= 5)
);

CREATE TABLE movie_genres (
    movie_id UUID NOT NULL REFERENCES movies (id) ON DELETE CASCADE,
    genre    VARCHAR(32) NOT NULL,
    PRIMARY KEY (movie_id, genre)
);
