package io.haoblog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import io.haoblog.site.web.PublicSiteController;
import io.haoblog.site.application.SiteService;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import io.haoblog.shared.web.ProblemResponseWriter;

@WebMvcTest(PublicSiteController.class)
@Import(ProblemResponseWriter.class)
class HaoBlogApplicationTests {
    @MockitoBean SiteService siteService;
    @Test
    void contextLoads() {
    }
}
