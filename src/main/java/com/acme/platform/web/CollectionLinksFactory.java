package com.acme.platform.web;

import com.acme.generated.model.CollectionLinks;
import com.acme.generated.model.Link;
import com.acme.shared.domain.paging.Page;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Builds the criteria-preserving navigation links every collection response carries (design D6,
 * {@code platform/collection-paging}). Reusable by every future collection operation: it reads the
 * current request itself, rather than requiring the caller to inject one.
 */
@Component
public class CollectionLinksFactory {

  public CollectionLinks create(Page<?> page, Set<String> recognisedParams) {
    HttpServletRequest request = currentRequest();
    List<String> keptPairs = keepRecognised(request.getQueryString(), recognisedParams);

    int totalPages = page.totalPages();
    int lastPage = Math.max(totalPages - 1, 0);
    int requestedPage = page.request().page();
    String basePath = ServletUriComponentsBuilder.fromRequestUri(request).build().toUriString();

    Link self = linkForPage(basePath, keptPairs, requestedPage);
    Link first = linkForPage(basePath, keptPairs, 0);
    Link last = linkForPage(basePath, keptPairs, lastPage);

    CollectionLinks links = new CollectionLinks(self, first, last);
    if (requestedPage > 0 && requestedPage <= lastPage) {
      links.prev(linkForPage(basePath, keptPairs, requestedPage - 1));
    }
    if (requestedPage < lastPage) {
      links.next(linkForPage(basePath, keptPairs, requestedPage + 1));
    }
    return links;
  }

  private HttpServletRequest currentRequest() {
    ServletRequestAttributes attributes =
        (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
    return attributes.getRequest();
  }

  /**
   * Keeps only the raw query pairs (exactly as encoded, in first-appearance order) whose decoded
   * name is a recognised parameter, dropping {@code page} (it is re-appended last with the target
   * page, per the D6/collection-paging convention).
   */
  private List<String> keepRecognised(String rawQuery, Set<String> recognisedParams) {
    List<String> kept = new ArrayList<>();
    if (rawQuery == null || rawQuery.isEmpty()) {
      return kept;
    }
    for (String pair : rawQuery.split("&", -1)) {
      if (pair.isEmpty()) {
        continue;
      }
      int equalsIndex = pair.indexOf('=');
      String rawName = equalsIndex >= 0 ? pair.substring(0, equalsIndex) : pair;
      Optional<String> name = decode(rawName);
      if (name.isEmpty() || name.get().equals("page")) {
        continue;
      }
      // A malformed percent-encoding (e.g. "title=%zz") is ignored by the container's parameter
      // parsing, so the search never used it; carrying it would make the link an invalid URI.
      boolean wellFormedValue =
          equalsIndex < 0 || decode(pair.substring(equalsIndex + 1)).isPresent();
      if (recognisedParams.contains(name.get()) && wellFormedValue) {
        kept.add(pair);
      }
    }
    return kept;
  }

  private static Optional<String> decode(String raw) {
    try {
      return Optional.of(URLDecoder.decode(raw, StandardCharsets.UTF_8));
    } catch (IllegalArgumentException malformed) {
      return Optional.empty();
    }
  }

  private Link linkForPage(String basePath, List<String> keptPairs, int targetPage) {
    StringBuilder query = new StringBuilder();
    for (String pair : keptPairs) {
      query.append(pair).append('&');
    }
    query.append("page=").append(targetPage);
    return new Link(URI.create(basePath + "?" + query));
  }
}
