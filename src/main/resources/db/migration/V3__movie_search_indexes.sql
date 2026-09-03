-- Supporting btree indexes for the movie search/list sort and genre-filter access paths
-- (CAT-002). Additive only — no table alteration; V1/V2 are never edited.

CREATE INDEX idx_movies_release_year ON movies (release_year);

CREATE INDEX idx_movies_rating ON movies (rating);

CREATE INDEX idx_movie_genre_genre_id ON movie_genre (genre_id);
