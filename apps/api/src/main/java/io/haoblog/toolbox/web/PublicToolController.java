package io.haoblog.toolbox.web;

import io.haoblog.toolbox.application.ToolManagementService;
import io.haoblog.toolbox.domain.ToolCategory;
import io.haoblog.toolbox.domain.ToolType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/tools")
public class PublicToolController {
    private final ToolManagementService service;

    public PublicToolController(ToolManagementService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Response> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) ToolType type,
            @RequestParam(required = false) String keyword,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        ToolManagementService.PublicTools result = service.listPublicTools(category, type, keyword);
        Response response = Response.from(result);
        String etag = representationHash(response);
        var headers = new HttpHeaders();
        headers.setETag(etag);
        headers.setCacheControl("public, max-age=0, s-maxage=60, must-revalidate");
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(304).headers(headers).build();
        }
        return ResponseEntity.ok().headers(headers).body(response);
    }

    private static String representationHash(Object value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return '"' + HexFormat.of().formatHex(digest.digest(value.toString().getBytes(StandardCharsets.UTF_8))) + '"';
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create public tools ETag", exception);
        }
    }

    public record Response(List<ToolResponse> items, List<CategoryResponse> categories) {
        static Response from(ToolManagementService.PublicTools value) {
            return new Response(value.items().stream().map(ToolResponse::from).toList(),
                    value.categories().stream().map(CategoryResponse::from).toList());
        }
    }

    public record CategoryResponse(UUID id, String name, String slug, String description, int sortOrder) {
        static CategoryResponse from(ToolCategory value) {
            return new CategoryResponse(value.getId(), value.getName(), value.getSlug(), value.getDescription(), value.getSortOrder());
        }
    }

    public record ToolResponse(UUID id, CategoryResponse category, ToolType type, String title, String slug,
                               String description, String url, String imageUrl, String componentKey, List<String> tags,
                               int sortOrder) {
        static ToolResponse from(ToolManagementService.PublicTool value) {
            return new ToolResponse(value.id(), CategoryResponse.from(value.category()), value.type(), value.title(), value.slug(),
                    value.description(), value.url(), value.imageUrl(), value.componentKey(), value.tags(), value.sortOrder());
        }
    }
}
