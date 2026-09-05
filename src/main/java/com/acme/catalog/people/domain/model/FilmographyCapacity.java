package com.acme.catalog.people.domain.model;

/**
 * The capacity a Person held on a single Movie for one {@link FilmographyEntry}. Two, and only two,
 * shapes exist — {@link ActingCapacity} or {@link NonActingCapacity} — each fully populated for its
 * kind, mirroring the {@code credits} table's per-type CHECK constraint (see {@code
 * openspec/changes/add-person-filmography/design.md}, D-A). Distinct from {@code
 * com.acme.catalog.credits.domain.model.Credit}, which additionally carries the {@code Person};
 * here the Person is already known (this is the person-side view) and only the Movie + capacity are
 * needed.
 */
public sealed interface FilmographyCapacity permits ActingCapacity, NonActingCapacity {}
