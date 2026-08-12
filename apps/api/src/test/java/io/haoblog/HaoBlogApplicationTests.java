package io.haoblog;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import io.haoblog.site.web.PublicSiteController;

@WebMvcTest(PublicSiteController.class)
class HaoBlogApplicationTests {
    @Test
    void contextLoads() {
    }
}
