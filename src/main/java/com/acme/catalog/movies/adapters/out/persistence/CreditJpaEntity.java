package com.acme.catalog.movies.adapters.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * JPA persistence model for a single credit row — either a {@code CAST} or {@code CREW} shape,
 * discriminated by {@code kind} (design.md decision 5: one table over two, since cast and crew are
 * always fetched together for a movie and this keeps the FK/index story simple). The DB-level
 * {@code credits_shape} CHECK constraint (see {@code V3__credits.sql}) enforces that only the
 * fields matching {@code kind} are populated. Lives only in {@code adapters/out/persistence} and is
 * mapped to/from domain {@code Cast}/{@code Crew} by {@link MovieCreditsPersistenceAdapter}.
 */
@Entity
@Table(name = "credits")
public class CreditJpaEntity {

  @Id private UUID id;

  @Column(name = "movie_id", nullable = false)
  private UUID movieId;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "person_id", nullable = false)
  private PersonJpaEntity person;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 4)
  private CreditKind kind;

  // cast-only
  @Column(name = "character")
  private String character;

  @Column(name = "billing_order")
  private Integer billingOrder;

  // crew-only
  private String department;

  private String job;

  protected CreditJpaEntity() {}

  private CreditJpaEntity(
      UUID id,
      UUID movieId,
      PersonJpaEntity person,
      CreditKind kind,
      String character,
      Integer billingOrder,
      String department,
      String job) {
    this.id = id;
    this.movieId = movieId;
    this.person = person;
    this.kind = kind;
    this.character = character;
    this.billingOrder = billingOrder;
    this.department = department;
    this.job = job;
  }

  public static CreditJpaEntity cast(
      UUID id, UUID movieId, PersonJpaEntity person, String character, int billingOrder) {
    return new CreditJpaEntity(
        id, movieId, person, CreditKind.CAST, character, billingOrder, null, null);
  }

  public static CreditJpaEntity crew(
      UUID id, UUID movieId, PersonJpaEntity person, String department, String job) {
    return new CreditJpaEntity(id, movieId, person, CreditKind.CREW, null, null, department, job);
  }

  public UUID getId() {
    return id;
  }

  public UUID getMovieId() {
    return movieId;
  }

  public PersonJpaEntity getPerson() {
    return person;
  }

  public CreditKind getKind() {
    return kind;
  }

  public String getCharacter() {
    return character;
  }

  public Integer getBillingOrder() {
    return billingOrder;
  }

  public String getDepartment() {
    return department;
  }

  public String getJob() {
    return job;
  }
}
