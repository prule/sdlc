package com.acme.catalog.credits.adapters.out.persistence;

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
 * JPA row for a Credit — links a {@link PersonJpaEntity} to a Movie (by plain {@code movie_id}
 * column; the Movie aggregate is out of this package's concern) in either a {@code CAST} or a
 * {@code CREW} capacity, mirroring the {@code credits} table's per-type CHECK constraint. JPA
 * annotations live only in this package — never on the domain.
 */
@Entity
@Table(name = "credits")
public class CreditJpaEntity {

  @Id private UUID id;

  @Column(name = "movie_id", nullable = false)
  private UUID movieId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "person_id", nullable = false)
  private PersonJpaEntity person;

  @Enumerated(EnumType.STRING)
  @Column(name = "credit_type", nullable = false, length = 10)
  private CreditType creditType;

  @Column(name = "character_name")
  private String characterName;

  @Column(name = "billing_order")
  private Integer billingOrder;

  @Column private String department;

  @Column private String job;

  protected CreditJpaEntity() {}

  public static CreditJpaEntity cast(
      UUID id, UUID movieId, PersonJpaEntity person, String characterName, int billingOrder) {
    CreditJpaEntity entity = new CreditJpaEntity();
    entity.id = id;
    entity.movieId = movieId;
    entity.person = person;
    entity.creditType = CreditType.CAST;
    entity.characterName = characterName;
    entity.billingOrder = billingOrder;
    return entity;
  }

  public static CreditJpaEntity crew(
      UUID id, UUID movieId, PersonJpaEntity person, String department, String job) {
    CreditJpaEntity entity = new CreditJpaEntity();
    entity.id = id;
    entity.movieId = movieId;
    entity.person = person;
    entity.creditType = CreditType.CREW;
    entity.department = department;
    entity.job = job;
    return entity;
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

  public CreditType getCreditType() {
    return creditType;
  }

  public String getCharacterName() {
    return characterName;
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
