CREATE TABLE genre (
  id   UUID PRIMARY KEY,
  name VARCHAR(64) NOT NULL UNIQUE
);

CREATE TABLE movie (
  id              UUID PRIMARY KEY,
  title           VARCHAR(500) NOT NULL CHECK (char_length(title) > 0),
  release_year    INTEGER NOT NULL,
  runtime_minutes INTEGER NULL CHECK (runtime_minutes > 0),
  synopsis        TEXT NULL,
  rating          NUMERIC(2,1) NULL CHECK (rating >= 0 AND rating <= 5)
);

CREATE TABLE movie_genre (
  movie_id UUID NOT NULL REFERENCES movie(id),
  genre_id UUID NOT NULL REFERENCES genre(id),
  PRIMARY KEY (movie_id, genre_id)
);

CREATE INDEX movie_genre_genre_idx ON movie_genre (genre_id);
