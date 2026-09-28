-- Standalone-only demo seed (design D7). Never applied in persistent (`postgres` profile) mode:
-- `application-postgres.yml` and PostgresIntegrationTest both pin `spring.flyway.locations` to
-- exclude this location. Plain literal INSERTs only, portable across H2 (MODE=PostgreSQL) and
-- PostgreSQL.

INSERT INTO genre (id, name) VALUES
  ('a0000000-0000-4000-8000-000000000001', 'Drama'),
  ('a0000000-0000-4000-8000-000000000002', 'Sci-Fi'),
  ('a0000000-0000-4000-8000-000000000003', 'Comedy'),
  ('a0000000-0000-4000-8000-000000000004', 'Thriller');

-- Fully populated: every optional field present, genres linked out of A-Z order.
INSERT INTO movie (id, title, release_year, runtime_minutes, synopsis, rating) VALUES
  ('11111111-1111-4111-8111-111111111111', 'Arrival', 2016, 116,
   'A linguist is recruited to communicate with alien visitors.', 4.5);
INSERT INTO movie_genre (movie_id, genre_id) VALUES
  ('11111111-1111-4111-8111-111111111111', 'a0000000-0000-4000-8000-000000000002'),
  ('11111111-1111-4111-8111-111111111111', 'a0000000-0000-4000-8000-000000000001');

-- No optional fields at all, and no genres.
INSERT INTO movie (id, title, release_year, runtime_minutes, synopsis, rating) VALUES
  ('22222222-2222-4222-8222-222222222222', 'Untitled Reel', 1974, NULL, NULL, NULL);

-- A couple more sample movies for a fuller catalog.
INSERT INTO movie (id, title, release_year, runtime_minutes, synopsis, rating) VALUES
  ('33333333-3333-4333-8333-333333333333', 'The Grand Heist', 2005, 128,
   'A crew plans one last job.', 3.5);
INSERT INTO movie_genre (movie_id, genre_id) VALUES
  ('33333333-3333-4333-8333-333333333333', 'a0000000-0000-4000-8000-000000000004');

INSERT INTO movie (id, title, release_year, runtime_minutes, synopsis, rating) VALUES
  ('44444444-4444-4444-8444-444444444444', 'Laugh Track', 1998, 95, NULL, 3.0);
INSERT INTO movie_genre (movie_id, genre_id) VALUES
  ('44444444-4444-4444-8444-444444444444', 'a0000000-0000-4000-8000-000000000003');
