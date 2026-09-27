package com.acme.platform.interfacedescription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.testsupport.PostgresIntegrationTest;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The browsable interface description (design D5, task 6.6): served publicly in every mode, with no
 * inline {@code <script>} body so the strict CSP (no {@code 'unsafe-inline'} for scripts) still
 * lets it run.
 */
@AutoConfigureMockMvc
class BrowsableInterfaceDescriptionTest extends PostgresIntegrationTest {

  private static final Pattern SCRIPT_TAG = Pattern.compile("<script[^>]*>([\\s\\S]*?)</script>");

  @Autowired private MockMvc mockMvc;

  @Test
  void pageAndEveryReferencedAssetRespond200() throws Exception {
    String html =
        mockMvc
            .perform(get("/swagger-ui/index.html"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.TEXT_HTML))
            .andReturn()
            .getResponse()
            .getContentAsString();

    for (String assetPath :
        List.of(
            "/webjars/swagger-ui/5.18.2/swagger-ui.css",
            "/webjars/swagger-ui/5.18.2/swagger-ui-bundle.js",
            "/webjars/swagger-ui/5.18.2/swagger-ui-standalone-preset.js",
            "/swagger-ui/swagger-initializer.js")) {
      assertThat(html).contains(assetPath);
      mockMvc.perform(get(assetPath)).andExpect(status().isOk());
    }
  }

  @Test
  void thePageHasNoInlineScriptBody() throws Exception {
    String html =
        mockMvc
            .perform(get("/swagger-ui/index.html"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    Matcher matcher = SCRIPT_TAG.matcher(html);
    while (matcher.find()) {
      assertThat(matcher.group(1)).isBlank();
    }
  }
}
