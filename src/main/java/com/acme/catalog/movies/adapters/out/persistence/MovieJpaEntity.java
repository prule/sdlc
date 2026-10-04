package com.acme.catalog.movies.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** JPA mapping for the {@code movie} table (design D5). */
@Entity
@Table(name = "movie")
public class MovieJpaEntity {

  @Id private UUID id;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "release_year", nullable = false)
  private int releaseYear;

  @Column(name = "runtime_minutes")
  private Integer runtimeMinutes;

  @Column(name = "synopsis")
  private String synopsis;

  @Column(name = "rating")
  private BigDecimal rating;

  @ManyToMany
  @JoinTable(
      name = "movie_genre",
      joinColumns = @JoinColumn(name = "movie_id"),
      inverseJoinColumns = @JoinColumn(name = "genre_id"))
  private Set<GenreJpaEntity> genres = new HashSet<>();

  protected MovieJpaEntity() {}

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

  public Set<GenreJpaEntity> getGenres() {
    return genres;
  }
}
