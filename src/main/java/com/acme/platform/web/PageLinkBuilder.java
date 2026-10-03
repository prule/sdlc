package com.acme.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.ForwardedHeaderUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

/**
 * Assembles the navigation links of a paged list from the current request (BR-7, design D6 of
 * add-movie-search). Reusable by every paged list: the caller supplies only the names of the
 * request parameters it recognises, in a fixed order, and the page numbers.
 *
 * <p>Each href is absolute (honouring forwarded scheme and host) and targets the current path. It
 * carries every recognised parameter the request had, with its values exactly as sent (repeated and
 * blank values included); parameters the request omitted stay omitted, so defaults are never
 * written into a link, and unrecognised parameters are dropped. Only {@code page} differs between
 * links: {@code self} repeats the request's raw {@code page} (or none), the others set it to their
 * target page.
 */
@Component
public class PageLinkBuilder {

  static final String PAGE = "page";

  /** The hrefs of one page's navigation links; {@code prev}/{@code next} only where they exist. */
  public record PageHrefs(URI self, URI first, URI last, Optional<URI> prev, Optional<URI> next) {

    public PageHrefs {
      Objects.requireNonNull(self, "self");
      Objects.requireNonNull(first, "first");
      Objects.requireNonNull(last, "last");
      Objects.requireNonNull(prev, "prev");
      Objects.requireNonNull(next, "next");
    }
  }

  /**
   * @param recognisedParameters the names of the list's recognised parameters other than {@code
   *     page}, in the order they are written into each href
   * @param page the requested page
   * @param lastPage the index of the last page ({@code 0} when nothing matches)
   * @param hasPrevious whether a previous page exists
   * @param hasNext whether a next page exists
   */
  public PageHrefs build(
      List<String> recognisedParameters,
      int page,
      int lastPage,
      boolean hasPrevious,
      boolean hasNext) {
    HttpServletRequest request = currentRequest();
    UriComponentsBuilder base = baseUri(request);
    for (String name : recognisedParameters) {
      addVerbatim(base, name, request.getParameterValues(name));
    }

    UriComponentsBuilder self = base.cloneBuilder();
    addVerbatim(self, PAGE, request.getParameterValues(PAGE));

    return new PageHrefs(
        toUri(self),
        withPage(base, 0),
        withPage(base, lastPage),
        hasPrevious ? Optional.of(withPage(base, page - 1)) : Optional.empty(),
        hasNext ? Optional.of(withPage(base, page + 1)) : Optional.empty());
  }

  private static HttpServletRequest currentRequest() {
    if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
      return attrs.getRequest();
    }
    throw new IllegalStateException("No current HTTP request to build page links from");
  }

  /** The current request's absolute URI, forwarded headers applied, with no query or fragment. */
  private static UriComponentsBuilder baseUri(HttpServletRequest request) {
    ServletServerHttpRequest httpRequest = new ServletServerHttpRequest(request);
    return ForwardedHeaderUtils.adaptFromForwardedHeaders(
            httpRequest.getURI(), httpRequest.getHeaders())
        .replaceQuery(null)
        .fragment(null);
  }

  private static void addVerbatim(UriComponentsBuilder builder, String name, String[] values) {
    if (values == null) {
      return;
    }
    for (String value : values) {
      builder.queryParam(encode(name), encode(value));
    }
  }

  private static URI withPage(UriComponentsBuilder base, int page) {
    return toUri(base.cloneBuilder().queryParam(PAGE, page));
  }

  /**
   * Every query name and value is already strictly percent-encoded (so {@code &}, {@code +}, ...).
   */
  private static URI toUri(UriComponentsBuilder builder) {
    return builder.build(true).toUri();
  }

  private static String encode(String value) {
    return UriUtils.encode(value, StandardCharsets.UTF_8);
  }
}
