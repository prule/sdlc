package com.acme.catalog.movies.application.port.out;

import java.util.Set;

/** Outbound port for loading the curated genre vocabulary's names (design D5). */
public interface LoadGenreVocabularyPort {

  Set<String> loadGenreNames();
}
