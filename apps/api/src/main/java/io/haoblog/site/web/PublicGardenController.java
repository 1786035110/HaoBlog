package io.haoblog.site.web;

import io.haoblog.site.application.GardenGraphService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

@RestController
@RequestMapping("/api/v1/public/garden")
public class PublicGardenController {
    private final GardenGraphService service;

    public PublicGardenController(GardenGraphService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<Response> garden(@RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        Response response = Response.from(service.get());
        String etag = representationHash(response);
        var headers = new HttpHeaders();
        headers.setETag(etag);
        headers.setCacheControl("public, max-age=0, s-maxage=60, must-revalidate");
        if (etag.equals(ifNoneMatch)) return ResponseEntity.status(304).headers(headers).build();
        return ResponseEntity.ok().headers(headers).body(response);
    }

    private static String representationHash(Object value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return '"' + HexFormat.of().formatHex(digest.digest(value.toString().getBytes(StandardCharsets.UTF_8))) + '"';
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create garden representation ETag", exception);
        }
    }

    public record Response(List<Node> nodes, List<Edge> edges, boolean truncated) {
        static Response from(GardenGraphService.Graph graph) {
            return new Response(graph.nodes().stream().map(Node::from).toList(),
                    graph.edges().stream().map(Edge::from).toList(), graph.truncated());
        }
    }

    public record Node(String id, GardenGraphService.NodeType type, String label, String href,
                       String summary, java.time.Instant publishedAt, int degree) {
        static Node from(GardenGraphService.Node value) {
            return new Node(value.id(), value.type(), value.label(), value.href(), value.summary(),
                    value.publishedAt(), value.degree());
        }
    }

    public record Edge(String source, String target, GardenGraphService.EdgeKind kind, int weight) {
        static Edge from(GardenGraphService.Edge value) {
            return new Edge(value.source(), value.target(), value.kind(), value.weight());
        }
    }
}
