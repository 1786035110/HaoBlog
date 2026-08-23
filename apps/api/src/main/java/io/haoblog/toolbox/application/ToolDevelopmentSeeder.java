package io.haoblog.toolbox.application;

import io.haoblog.toolbox.domain.Tool;
import io.haoblog.toolbox.domain.ToolCategory;
import io.haoblog.toolbox.domain.ToolComponentKey;
import io.haoblog.toolbox.domain.ToolStatus;
import io.haoblog.toolbox.domain.ToolType;
import io.haoblog.toolbox.persistence.ToolCategoryRepository;
import io.haoblog.toolbox.persistence.ToolRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Configuration
@Profile({"local", "dev"})
@ConditionalOnProperty(name = "haoblog.seed.enabled", havingValue = "true", matchIfMissing = false)
public class ToolDevelopmentSeeder {
    @Bean
    ApplicationRunner toolSeedRunner(ToolSeedService seedService) {
        return args -> seedService.seed();
    }

    @Configuration
    static class SeedServices {
        @Bean ToolSeedService toolSeedService(ToolCategoryRepository categories, ToolRepository tools, Clock clock) {
            return new ToolSeedService(categories, tools, clock);
        }
    }

    static class ToolSeedService {
        private final ToolCategoryRepository categories;
        private final ToolRepository tools;
        private final Clock clock;

        ToolSeedService(ToolCategoryRepository categories, ToolRepository tools, Clock clock) {
            this.categories = categories;
            this.tools = tools;
            this.clock = clock;
        }

        @Transactional
        public void seed() {
            Instant now = Instant.now(clock);
            ToolCategory category = categories.findAllByOrderBySortOrderAscNameAsc().stream()
                    .filter(value -> value.getSlug().equals("embedded-tools"))
                    .findFirst()
                    .orElseGet(() -> categories.saveAndFlush(new ToolCategory(
                            "浏览器内嵌工具", "embedded-tools", "输入留在浏览器，结果由原生 API 计算。", 10, now)));
            List<SeedTool> seedTools = List.of(
                    new SeedTool("json-format", "JSON 格式化", "校验、两空格格式化或压缩 JSON。", ToolComponentKey.JSON_FORMAT, List.of("json", "format")),
                    new SeedTool("base64", "Base64 编解码", "在浏览器中进行 UTF-8 文本 Base64 编解码。", ToolComponentKey.BASE64, List.of("base64", "utf8")),
                    new SeedTool("url-codec", "URL 编解码", "使用 encodeURIComponent 和 decodeURIComponent 处理 URI 组件。", ToolComponentKey.URL_CODEC, List.of("url", "encode")),
                    new SeedTool("timestamp", "时间戳转换", "自动识别秒/毫秒并显示本地时间与 UTC ISO。", ToolComponentKey.TIMESTAMP, List.of("time", "date")),
                    new SeedTool("regex-test", "正则测试", "在受控 Web Worker 中运行 JavaScript RegExp。", ToolComponentKey.REGEX_TEST, List.of("regex", "worker")));
            for (int index = 0; index < seedTools.size(); index++) {
                SeedTool item = seedTools.get(index);
                if (!tools.existsBySlug(item.slug())) {
                    tools.save(new Tool(category.getId(), ToolType.EMBEDDED, ToolStatus.ACTIVE, item.title(), item.slug(),
                            item.description(), null, null, item.componentKey(), item.tags(), index + 1, now));
                }
            }
            tools.flush();
        }

        private record SeedTool(String slug, String title, String description, ToolComponentKey componentKey, List<String> tags) {}
    }
}
