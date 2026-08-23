package io.haoblog;

import io.haoblog.content.application.ArticleService;
import io.haoblog.content.web.PublicSearchController;
import io.haoblog.shared.web.GlobalExceptionHandler;
import io.haoblog.shared.web.ProblemResponseWriter;
import io.haoblog.shared.web.TraceIdFilter;
import io.haoblog.site.application.SiteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PublicSearchController.class)
@Import({TraceIdFilter.class, GlobalExceptionHandler.class, ProblemResponseWriter.class})
class PublicSearchTest {
    @Autowired MockMvc mvc;
    @MockitoBean ArticleService articleService;
    @MockitoBean SiteService siteService;

    @Test
    void validatesSearchAndReturnsSafeProblemForInvalidInput() throws Exception {
        when(articleService.search(any(), any(Integer.class), any(Integer.class)))
                .thenThrow(new IllegalArgumentException("query/page/size out of range"));

        for (String query : new String[]{"", "a", "a".repeat(101)}) {
            mvc.perform(get("/api/v1/public/search/articles").param("q", query))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }
        mvc.perform(get("/api/v1/public/search/articles").param("q", "valid").param("size", "21"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void returnsSummaryOnlyAndSupportsSharedConditionalCaching() throws Exception {
        var article = new ArticleService.PublicSearchArticle(
                java.util.UUID.randomUUID(), "visible", "Visible result", "Excerpt", Instant.parse("2026-01-01T00:00:00Z"), null, true);
        when(articleService.search("signal", 0, 20)).thenReturn(new ArticleService.SearchPage(
                "signal", new PageImpl<>(List.of(article), PageRequest.of(0, 20), 1)));
        when(articleService.publicCoverUrls(any())).thenReturn(java.util.Map.of());
        when(siteService.get()).thenReturn(new SiteService.SiteResult("HaoBlog", "Night station", "https://blog.example.test", "Hao"));

        var first = mvc.perform(get("/api/v1/public/search/articles").param("q", "signal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].title").value("Visible result"))
                .andExpect(jsonPath("$.items[0].excerpt").value("Excerpt"))
                .andExpect(jsonPath("$.items[0].markdown").doesNotExist())
                .andExpect(header().string("Cache-Control", "public, max-age=0, s-maxage=60, must-revalidate"))
                .andExpect(header().exists("ETag"))
                .andReturn();

        mvc.perform(get("/api/v1/public/search/articles").param("q", "signal")
                        .header("If-None-Match", first.getResponse().getHeader("ETag")))
                .andExpect(status().isNotModified())
                .andExpect(content().string(""));
    }
}
