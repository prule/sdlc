package com.acme.catalog.movies.adapters.in.web;

import com.acme.generated.model.Link;
import com.acme.generated.model.PageLinks;
import com.acme.platform.web.PageLinkBuilder;
import com.acme.platform.web.PageLinkBuilder.PageHrefs;
import com.acme.shared.domain.ResultPage;
import java.util.List;

/**
 * The navigation links of a movie search page (design D6): declares the movie-search parameters
 * that links carry, delegates assembly to the platform {@link PageLinkBuilder}, and maps the result
 * to the generated {@link PageLinks}.
 */
final class MovieSearchLinks {

  /** Every recognised movie-search parameter except {@code page}, in the order links carry them. */
  static final List<String> RECOGNISED_PARAMETERS =
      List.of("title", "genre", "releaseYearFrom", "releaseYearTo", "minRating", "sort", "size");

  private final PageLinkBuilder pageLinkBuilder;

  MovieSearchLinks(PageLinkBuilder pageLinkBuilder) {
    this.pageLinkBuilder = pageLinkBuilder;
  }

  PageLinks linksFor(ResultPage<?> page) {
    PageHrefs hrefs =
        pageLinkBuilder.build(
            RECOGNISED_PARAMETERS,
            page.page(),
            page.lastPage(),
            page.hasPrevious(),
            page.hasNext());
    PageLinks links =
        new PageLinks(new Link(hrefs.self()), new Link(hrefs.first()), new Link(hrefs.last()));
    hrefs.prev().ifPresent(prev -> links.prev(new Link(prev)));
    hrefs.next().ifPresent(next -> links.next(new Link(next)));
    return links;
  }
}
