package com.acme.catalog.movies.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** JPA row for a Movie. JPA annotations live only in this package — never on the domain. */
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

  @Column(columnDefinition = "text")
  private String synopsis;

  private BigDecimal rating;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "movie_genre",
      joinColumns = @JoinColumn(name = "movie_id"),
      inverseJoinColumns = @JoinColumn(name = "genre_id"))
  private Set<GenreJpaEntity> genres = new LinkedHashSet<>();

  protected MovieJpaEntity() {}

  public MovieJpaEntity(
      UUID id,
      String title,
      int releaseYear,
      Integer runtimeMinutes,
      String synopsis,
      BigDecimal rating,
      Set<GenreJpaEntity> genres) {
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

  public Set<GenreJpaEntity> getGenres() {
    return genres;
  }
}
