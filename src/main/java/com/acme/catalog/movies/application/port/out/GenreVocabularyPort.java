package com.acme.catalog.movies.application.port.out;

import java.util.Set;

/** Outbound port to the curated genre vocabulary (UC-002 BR-4, BR-10). */
public interface GenreVocabularyPort {

  /**
   * @param lowerCasedNames genre names, lower-cased
   * @return those of the given names that name no genre in the vocabulary, ignoring letter case
   */
  Set<String> unknownGenres(Set<String> lowerCasedNames);
}
