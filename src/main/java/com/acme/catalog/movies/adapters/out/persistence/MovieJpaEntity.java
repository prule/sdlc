package com.acme.catalog.movies.adapters.out.persistence;

import com.acme.catalog.movies.domain.model.Genre;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * JPA persistence model for a movie. Lives only in {@code adapters/out/persistence} and is mapped
 * to/from the domain {@link com.acme.catalog.movies.domain.model.Movie} by {@link
 * MoviePersistenceAdapter} — never exposed outside this package.
 */
@Entity
@Table(name = "movies")
public class MovieJpaEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String title;

  @Column(name = "release_year", nullable = false)
  private int releaseYear;

  @Column(name = "runtime_minutes")
  private Integer runtimeMinutes;

  private String synopsis;

  private BigDecimal rating;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "movie_genres", joinColumns = @JoinColumn(name = "movie_id"))
  @Enumerated(EnumType.STRING)
  @Column(name = "genre", nullable = false)
  private Set<Genre> genres = new HashSet<>();

  protected MovieJpaEntity() {}

  public MovieJpaEntity(
      UUID id,
      String title,
      int releaseYear,
      Integer runtimeMinutes,
      String synopsis,
      BigDecimal rating,
      Set<Genre> genres) {
    this.id = id;
    this.title = title;
    this.releaseYear = releaseYear;
    this.runtimeMinutes = runtimeMinutes;
    this.synopsis = synopsis;
    this.rating = rating;
    this.genres = genres;
  }

  public UUID getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public int getReleaseYear() {
    return releaseYear;
  }

  public Integer getRuntimeMinutes() {
    return runtimeMinutes;
  }

  public String getSynopsis() {
    return synopsis;
  }

  public BigDecimal getRating() {
    return rating;
  }

  public Set<Genre> getGenres() {
    return genres;
  }
}
