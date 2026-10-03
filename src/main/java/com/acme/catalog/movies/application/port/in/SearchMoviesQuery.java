package com.acme.catalog.movies.application.port.in;

import java.math.BigDecimal;
import java.util.List;

/**
 * A movie search exactly as asked (UC-002 step 1): raw, unvalidated values, any of which may be
 * {@code null}. {@link SearchMoviesUseCase} decides whether it is asked in an allowed way.
 */
public record SearchMoviesQuery(
    String title,
    List<String> genres,
    Integer releaseYearFrom,
    Integer releaseYearTo,
    BigDecimal minRating,
    String sort,
    Integer page,
    Integer size) {}
