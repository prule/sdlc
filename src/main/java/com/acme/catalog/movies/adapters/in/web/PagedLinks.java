package com.acme.catalog.movies.adapters.in.web;

import com.acme.catalog.movies.domain.model.ResultPage;
import com.acme.generated.model.Link;
import com.acme.generated.model.MovieSearchLinks;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.StringJoiner;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Builds a paged list's navigation links (UC-002 BR-7, {@code platform/uniform-responses} "Paged
 * collection form", design D6). Every link is the current request's URI with the same declared
 * query parameters, values exactly as sent, and only {@code page} changed; parameters the client
 * omitted (and undeclared ones) are never added. Web-adapter only.
 */
final class PagedLinks {

  private static final String PAGE = "page";

  private final List<String> declaredParameters;

  /**
   * @param declaredParameters the operation's query parameters as the interface description names
   *     them, in the order they are written onto links
   */
  PagedLinks(List<String> declaredParameters) {
    this.declaredParameters = List.copyOf(declaredParameters);
  }

  MovieSearchLinks linksFor(HttpServletRequest request, ResultPage<?> page) {
    String base =
        ServletUriComponentsBuilder.fromCurrentRequestUri().replaceQuery(null).toUriString();
    String criteria = criteriaQuery(request);
    boolean pageWasSent = request.getParameter(PAGE) != null;

    MovieSearchLinks links =
        new MovieSearchLinks(
            new Link(pageWasSent ? uri(base, criteria, page.page()) : uri(base, criteria)),
            new Link(uri(base, criteria, 0)),
            new Link(uri(base, criteria, page.lastPage())));
    if (page.hasPrev()) {
      links.prev(new Link(uri(base, criteria, page.page() - 1)));
    }
    if (page.hasNext()) {
      links.next(new Link(uri(base, criteria, page.page() + 1)));
    }
    return links;
  }

  private String criteriaQuery(HttpServletRequest request) {
    StringJoiner query = new StringJoiner("&");
    for (String name : declaredParameters) {
      String[] values = name.equals(PAGE) ? null : request.getParameterValues(name);
      if (values != null) {
        for (String value : values) {
          query.add(encode(name) + "=" + encode(value));
        }
      }
    }
    return query.toString();
  }

  private static URI uri(String base, String criteria, int page) {
    String query = criteria.isEmpty() ? PAGE + "=" + page : criteria + "&" + PAGE + "=" + page;
    return URI.create(base + "?" + query);
  }

  private static URI uri(String base, String criteria) {
    return URI.create(criteria.isEmpty() ? base : base + "?" + criteria);
  }

  /** Strict query encoding: {@code +}, {@code %}, {@code &} and {@code =} never change meaning. */
  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
  }
}
