-- Supports the person-filmography load path (CAT-005): filtering credits by person_id, then
-- joining to movies for the releaseYear-desc/title-asc/credit-id ordering. credits already has
-- indexes keyed on movie_id (V4) but none on person_id, so this lookup would otherwise be a full
-- table scan of credits for every filmography request. Additive only — V1-V4 are never edited.

CREATE INDEX idx_credits_person_id ON credits (person_id);
