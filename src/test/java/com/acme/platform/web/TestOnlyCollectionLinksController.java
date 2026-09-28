package com.acme.platform.web;

import com.acme.generated.model.CollectionLinks;
import com.acme.shared.domain.paging.Page;
import com.acme.shared.domain.paging.PageRequest;
import java.util.List;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exposes {@link CollectionLinksFactory} for a real MockMvc round trip, so forwarded-header
 * honouring can be asserted end-to-end (design D6, task 6.2). Test-only.
 */
@RestController
public class TestOnlyCollectionLinksController {

  private final CollectionLinksFactory collectionLinksFactory;

  public TestOnlyCollectionLinksController(CollectionLinksFactory collectionLinksFactory) {
    this.collectionLinksFactory = collectionLinksFactory;
  }

  @GetMapping("/test-only/collection-links/movies")
  public CollectionLinks movies() {
    Page<String> page = new Page<>(List.of(), new PageRequest(0, 20), 3);
    return collectionLinksFactory.create(
        page,
        Set.of(
            "title",
            "genre",
            "releaseYearFrom",
            "releaseYearTo",
            "minRating",
            "sort",
            "page",
            "size"));
  }
}
