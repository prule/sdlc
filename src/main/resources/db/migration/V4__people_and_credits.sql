-- Introduces Person/Credit modelling (CAT-003): a movie's cast and crew. Additive only —
-- V1-V3 are never edited.

CREATE TABLE people (
    id uuid PRIMARY KEY,
    name varchar(500) NOT NULL
);

CREATE TABLE credits (
    id uuid PRIMARY KEY,
    movie_id uuid NOT NULL,
    person_id uuid NOT NULL,
    credit_type varchar(10) NOT NULL,
    character_name varchar(500),
    billing_order integer,
    department varchar(200),
    job varchar(200),
    CONSTRAINT fk_credits_movie FOREIGN KEY (movie_id) REFERENCES movies (id),
    CONSTRAINT fk_credits_person FOREIGN KEY (person_id) REFERENCES people (id),
    CONSTRAINT chk_credits_type CHECK (credit_type IN ('CAST', 'CREW')),
    -- Per-type invariant: a CAST row always carries character_name + billing_order and never
    -- department/job; a CREW row is the inverse. Enforced at the DB, not just in application code.
    CONSTRAINT chk_credits_cast_fields CHECK (
        (credit_type = 'CAST'
            AND character_name IS NOT NULL
            AND billing_order IS NOT NULL
            AND department IS NULL
            AND job IS NULL)
        OR
        (credit_type = 'CREW'
            AND department IS NOT NULL
            AND job IS NOT NULL
            AND character_name IS NULL
            AND billing_order IS NULL)
    )
);

-- Supports the existence-probe + fetch-join load pattern (movie_id) and the deterministic
-- in-SQL ordering (billing_order for cast; department/job for crew).
CREATE INDEX idx_credits_movie_id ON credits (movie_id);
CREATE INDEX idx_credits_movie_billing_order ON credits (movie_id, billing_order);
CREATE INDEX idx_credits_movie_department_job ON credits (movie_id, department, job);
