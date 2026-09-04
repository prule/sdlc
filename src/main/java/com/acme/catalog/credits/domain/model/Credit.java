package com.acme.catalog.credits.domain.model;

/**
 * A Credit links a {@link Person} to a Movie in a specific capacity. Two, and only two, shapes
 * exist — an acting credit ({@link CastCredit}) or a non-acting credit ({@link CrewCredit}) — each
 * with its own fully-populated fields and ordering, rather than a single shape with a discriminator
 * and half-absent fields (see {@code openspec/changes/add-movie-credits/design.md}, D-A).
 */
public sealed interface Credit permits CastCredit, CrewCredit {

  Person person();
}
