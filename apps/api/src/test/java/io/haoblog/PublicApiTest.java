package io.haoblog;

import io.haoblog.content.web.PublicArticleController;
import io.haoblog.site.web.PublicSiteController;
import org.junit.jupiter.api.Test;
import io.haoblog.content.domain.Article;
import io.haoblog.content.domain.ArticleStatus;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.time.Instant;
import java.util.Optional;
import java.util.List;
import static org.mockito.Mockito.mock;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.BeforeEach;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.jayway.jsonpath.JsonPath;

@WebMvcTest({PublicSiteController.class, PublicArticleController.class})
@Import({io.haoblog.shared.web.TraceIdFilter.class, io.haoblog.shared.web.GlobalExceptionHandler.class})
class PublicApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean io.haoblog.site.application.SiteService siteService;
    @MockitoBean io.haoblog.content.application.ArticleService articleService;

    @BeforeEach
    void rejectInvalidServiceArguments() {
        when(articleService.list(anyInt(), anyInt())).thenThrow(new IllegalArgumentException("page/size out of range"));
    }

    @Test void invalidPaginationReturnsSafeProblem() throws Exception {
        for (String query : new String[]{"?page=-1", "?size=0", "?size=51", "?page=abc"}) {
            mvc.perform(get("/api/v1/public/articles" + query))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                    .andExpect(jsonPath("$.title").value("Invalid request"))
                    .andExpect(jsonPath("$.detail").value("Request parameters are invalid"))
                    .andExpect(jsonPath("$.traceId").isString())
                    .andExpect(header().exists("X-Request-ID"))
                    .andDo(result -> assertEquals(
                            result.getResponse().getHeader("X-Request-ID"),
                            JsonPath.read(result.getResponse().getContentAsString(), "$.traceId")));
        }
    }

    @Test void unknownApiAndStaticResourceReturnProblems() throws Exception {
        for (String path : new String[]{"/api/v1/public/unknown", "/missing.css"}) {
            mvc.perform(get(path).header("X-Request-ID", "request-123"))
                    .andExpect(status().isNotFound()).andExpect(content().contentType("application/problem+json"))
                    .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                    .andExpect(jsonPath("$.title").value("Resource not found"))
                    .andExpect(jsonPath("$.detail").isString())
                    .andExpect(jsonPath("$.traceId").value("request-123"))
                    .andExpect(header().string("X-Request-ID", "request-123"));
        }
    }

    @Test void unexpectedExceptionReturnsSafeProblem() throws Exception {
        when(siteService.get()).thenThrow(new IllegalStateException("secret SQL stack path"));
        mvc.perform(get("/api/v1/public/site").header("X-Request-ID", "request-500"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.title").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                .andExpect(jsonPath("$.traceId").value("request-500"))
                .andExpect(header().string("X-Request-ID", "request-500"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret SQL stack path"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("IllegalStateException"))));
    }

    @Test void articleDetailSupports304WithoutResponseBody() throws Exception {
        Instant publishedAt = Instant.parse("2026-01-01T00:00:00Z");
        when(articleService.findPublicBySlug("visible")).thenReturn(Optional.of(
                new Article("visible", "Visible", "Excerpt", "# Body", ArticleStatus.PUBLISHED, publishedAt, publishedAt)));
        var first = mvc.perform(get("/api/v1/public/articles/visible")).andExpect(status().isOk())
                .andExpect(jsonPath("$.markdown").value("# Body"))
                .andExpect(header().exists("ETag")).andReturn();
        String etag = first.getResponse().getHeader("ETag");
        mvc.perform(get("/api/v1/public/articles/visible").header("If-None-Match", etag))
                .andExpect(status().isNotModified()).andExpect(content().string(""));
    }

    @Test void missingArticleIsAProblemJson404() throws Exception {
        when(articleService.findPublicBySlug("missing")).thenReturn(Optional.empty());
        mvc.perform(get("/api/v1/public/articles/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType("application/problem+json"))
                .andExpect(jsonPath("$.code").value("ARTICLE_NOT_FOUND"))
                .andExpect(jsonPath("$.traceId").isString());
    }

    @Test void listEtagsAreIsolatedPerPageRepresentation() throws Exception {
        Instant publishedAt = Instant.parse("2026-01-01T00:00:00Z");
        Article article = new Article("visible", "Visible", "Excerpt", "# Body", ArticleStatus.PUBLISHED, publishedAt, publishedAt);
        var service = mock(io.haoblog.content.application.ArticleService.class);
        when(service.list(0, 20)).thenReturn(new io.haoblog.content.application.ArticleService.PageResult(new PageImpl<>(List.of(article), PageRequest.of(0, 20), 2)));
        when(service.list(1, 20)).thenReturn(new io.haoblog.content.application.ArticleService.PageResult(new PageImpl<>(List.of(), PageRequest.of(1, 20), 2)));
        var controller = new PublicArticleController(service);
        String first = controller.articles(0, 20, null).getHeaders().getETag();
        String second = controller.articles(1, 20, null).getHeaders().getETag();
        org.junit.jupiter.api.Assertions.assertNotEquals(first, second);
    }
}
