package com.acme.catalog.movies.domain.model;

/**
 * The domain-detected search criteria that can be invalid (design D2/D3). The web adapter maps each
 * to the query parameter name it corresponds to.
 */
public enum SearchCriterion {
  SORT,
  RELEASE_YEAR_RANGE,
  GENRE
}
