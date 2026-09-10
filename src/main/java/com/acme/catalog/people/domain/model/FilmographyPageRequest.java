package com.acme.catalog.people.domain.model;

/**
 * A requested page: zero-based page index and page size. Bound validation (page &gt;= 0, size
 * 1..100) happens at the web boundary via the generated {@code @Min}/{@code @Max} constraints —
 * this record merely carries the already-validated values through the domain/application layers.
 */
public record FilmographyPageRequest(int page, int size) {}
