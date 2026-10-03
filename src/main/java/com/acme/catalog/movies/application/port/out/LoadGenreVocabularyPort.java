package com.acme.catalog.movies.application.port.out;

import java.util.List;

/** Outbound port loading the curated genre vocabulary: every genre name, as curated (UC-002). */
public interface LoadGenreVocabularyPort {

  List<String> loadGenreNames();
}
