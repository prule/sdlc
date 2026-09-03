-- First real application tables: the catalog's Movie aggregate and its Genres.

CREATE TABLE genres (
    id uuid PRIMARY KEY,
    name varchar(100) NOT NULL,
    CONSTRAINT uq_genres_name UNIQUE (name)
);

CREATE TABLE movies (
    id uuid PRIMARY KEY,
    title varchar(500) NOT NULL,
    release_year integer NOT NULL,
    runtime_minutes integer,
    synopsis text,
    rating numeric(2, 1),
    CONSTRAINT chk_movies_rating CHECK (rating >= 0 AND rating <= 5)
);

CREATE TABLE movie_genre (
    movie_id uuid NOT NULL,
    genre_id uuid NOT NULL,
    PRIMARY KEY (movie_id, genre_id),
    CONSTRAINT fk_movie_genre_movie FOREIGN KEY (movie_id) REFERENCES movies (id),
    CONSTRAINT fk_movie_genre_genre FOREIGN KEY (genre_id) REFERENCES genres (id)
);
