package io.haoblog.comment.application;

import io.haoblog.comment.domain.Comment;
import io.haoblog.comment.domain.CommentStatus;
import io.haoblog.comment.persistence.CommentRepository;
import io.haoblog.content.application.ArticleCommentLookup;
import io.haoblog.identity.domain.AdminUser;
import io.haoblog.identity.persistence.AdminUserRepository;
import io.haoblog.shared.outbox.OutboxEvent;
import io.haoblog.shared.outbox.OutboxEventRepository;
import io.haoblog.shared.web.ProblemException;
import io.haoblog.site.application.SiteService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.text.Normalizer;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CommentService {
    private static final Pattern URL_SCHEME = Pattern.compile("(?i)\\b([a-z][a-z0-9+.-]*)://");
    private static final Pattern UNSAFE_SCHEME = Pattern.compile("(?i)(?:javascript|data|vbscript|file|mailto):");
    private static final Pattern RAW_HTML = Pattern.compile("(?is)<\\s*/?\\s*[a-z!][^>]*>");
    private static final Pattern HTTPS_LINK = Pattern.compile("(?i)https://");
    private static final int MAX_PAGE_SIZE = 50;
    private static final EnumSet<CommentStatus> MODERATABLE = EnumSet.of(
            CommentStatus.APPROVED, CommentStatus.SPAM, CommentStatus.REJECTED);

    private final CommentRepository comments;
    private final OutboxEventRepository outbox;
    private final ArticleCommentLookup articles;
    private final SiteService site;
    private final CommentSecurityService security;
    private final CommentChallengeService challenges;
    private final CommentRateLimiter rateLimiter;
    private final AdminUserRepository admins;
    private final Clock clock;

    @Autowired
    public CommentService(CommentRepository comments, OutboxEventRepository outbox,
                          ArticleCommentLookup articles, SiteService site,
                          CommentSecurityService security, CommentChallengeService challenges,
                          CommentRateLimiter rateLimiter, AdminUserRepository admins, Clock clock) {
        this.comments = comments;
        this.outbox = outbox;
        this.articles = articles;
        this.site = site;
        this.security = security;
        this.challenges = challenges;
        this.rateLimiter = rateLimiter;
        this.admins = admins;
        this.clock = clock;
    }

    public CommentService(CommentRepository comments, OutboxEventRepository outbox,
                          ArticleCommentLookup articles, SiteService site,
                          CommentSecurityService security, CommentChallengeService challenges,
                          CommentRateLimiter rateLimiter, Clock clock) {
        this(comments, outbox, articles, site, security, challenges, rateLimiter, null, clock);
    }

    @Transactional(readOnly = true)
    public Page<AdminComment> listAdmin(CommentStatus status, UUID articleId, String keyword,
                                        int page, int size, Sort.Direction direction) {
        validatePage(page, size);
        String normalizedKeyword = normalizeKeyword(keyword);
        var pageable = PageRequest.of(page, size);
        return comments.findAdminComments(status == null ? null : status.name(),
                articleId == null ? null : articleId.toString(), normalizedKeyword,
                direction.name().toLowerCase(Locale.ROOT), pageable).map(this::adminListView);
    }

    @Transactional(readOnly = true)
    public AdminCommentDetail getAdmin(UUID commentId) {
        Comment comment = comments.findById(commentId).orElseThrow(() -> notFound("COMMENT_NOT_FOUND"));
        return adminDetail(comment);
    }

    @Transactional
    public AdminCommentDetail moderate(UUID commentId, long expectedVersion, CommentStatus target,
                                       String reason, String username) {
        if (!MODERATABLE.contains(target)) {
            throw new ProblemException("COMMENT_MODERATION_STATE_CONFLICT", "Invalid moderation state",
                    "Comments can only be moderated to APPROVED, SPAM or REJECTED");
        }
        Comment comment = comments.findById(commentId).orElseThrow(() -> notFound("COMMENT_NOT_FOUND"));
        if (comment.getStatus() == CommentStatus.USER_DELETED) {
            throw new ProblemException("COMMENT_MODERATION_STATE_CONFLICT", "Invalid moderation state",
                    "A deleted comment cannot be moderated");
        }
        if (comment.getVersion() != expectedVersion) throw versionConflict(comment.getVersion());
        if (admins == null) throw new ProblemException("ADMIN_NOT_FOUND", "Administrator not found",
                "The administrator account is no longer available");
        UUID moderatorId = admins.findForAuthentication(username).map(AdminUser::getId)
                .orElseThrow(() -> new ProblemException("ADMIN_NOT_FOUND", "Administrator not found",
                        "The administrator account is no longer available"));
        comment.moderate(target, moderatorId, normalizeReason(reason), clock.instant());
        return adminDetail(comments.saveAndFlush(comment));
    }

    private AdminComment adminListView(Comment comment) {
        return AdminComment.from(comment, maskedEmail(comment));
    }

    private AdminCommentDetail adminDetail(Comment comment) {
        return AdminCommentDetail.from(comment, decryptedEmail(comment));
    }

    private String decryptedEmail(Comment comment) {
        return security.decryptEmail(comment.getId(), comment.getEmailCiphertext(), comment.getEmailNonce(), comment.getEmailKeyVersion());
    }

    private String maskedEmail(Comment comment) {
        String email = decryptedEmail(comment);
        if (email == null || email.isBlank()) return null;
        int at = email.lastIndexOf('@');
        if (at <= 0 || at == email.length() - 1) return "***";
        String local = email.substring(0, at);
        String visible = local.length() > 1 ? local.substring(0, 1) : "";
        return visible + "***@" + email.substring(at + 1);
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) throw new IllegalArgumentException("page/size out of range");
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) return null;
        String normalized = keyword.strip();
        if (normalized.length() > 240) throw new IllegalArgumentException("keyword is too long");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeReason(String reason) {
        return reason == null || reason.isBlank() ? null : reason.strip();
    }

    private static ProblemException notFound(String code) {
        return new ProblemException(code, "Comment not found", "The requested comment does not exist");
    }

    private static ProblemException versionConflict(long currentVersion) {
        return new ProblemException("COMMENT_VERSION_CONFLICT", "Comment version conflict",
                "Reload the latest comment before moderating it", currentVersion);
    }

    @Transactional
    public CommentPage list(String slug, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page/size out of range");
        }
        UUID articleId = publicArticle(slug).articleId();
        if (!site.get().commentsEnabled()) {
            return new CommentPage(List.of(), page, size, 0);
        }
        Page<Comment> topLevel = comments.findByArticleIdAndStatusAndParentIdIsNullOrderByCreatedAtAscIdAsc(
                articleId, CommentStatus.APPROVED, PageRequest.of(page, size));
        List<UUID> parentIds = topLevel.getContent().stream().map(Comment::getId).toList();
        Map<UUID, List<Comment>> replies = parentIds.isEmpty() ? Map.of() : comments
                .findByArticleIdAndStatusAndParentIdInOrderByCreatedAtAscIdAsc(
                        articleId, CommentStatus.APPROVED, parentIds)
                .stream().collect(java.util.stream.Collectors.groupingBy(
                        Comment::getParentId, LinkedHashMap::new, java.util.stream.Collectors.toList()));
        List<CommentView> items = topLevel.getContent().stream()
                .map(comment -> view(comment, replies.getOrDefault(comment.getId(), List.of())))
                .toList();
        return new CommentPage(items, topLevel.getNumber(), topLevel.getSize(), topLevel.getTotalElements());
    }

    @Transactional
    public CreateResult create(String slug, CreateCommand command, String visitorCookie, String remoteAddress) {
        ArticleCommentLookup.Target target = publicArticle(slug);
        if (!site.get().commentsEnabled() || !target.commentsEnabled()) {
            throw new ProblemException("COMMENTING_DISABLED", "Comments are disabled",
                    "Comments are not currently accepting new submissions");
        }
        String nickname = requiredTrimmed(command.nickname(), "COMMENT_NICKNAME_INVALID", "Nickname must be 2 to 40 characters");
        String content = requiredTrimmed(command.content(), "COMMENT_CONTENT_INVALID", "Content must be 2 to 2000 characters");
        String email = command.email() == null ? null : command.email().trim();
        validateLengths(nickname, content, email);
        validateContent(content);

        CommentChallengeService.Validation challenge = challenges.validateAndConsume(target.articleId(), command.challenge());
        if (challenge != CommentChallengeService.Validation.VALID) {
            throw switch (challenge) {
                case TOO_EARLY -> new ProblemException("COMMENT_CHALLENGE_TOO_EARLY", "Comment challenge submitted too soon",
                        "Please keep the form open for at least three seconds");
                case EXPIRED -> new ProblemException("COMMENT_CHALLENGE_EXPIRED", "Comment challenge expired",
                        "Request a new comment form challenge");
                default -> new ProblemException("COMMENT_CHALLENGE_INVALID", "Invalid comment challenge",
                        "Request a new comment form challenge");
            };
        }

        String honeypot = command.honeypot();
        if ((honeypot != null && !honeypot.isBlank()) || (command.website() != null && !command.website().isBlank())) {
            RateDecision rate = rate(visitorCookie, remoteAddress);
            if (!rate.allowed()) throw new CommentRateLimitException(rate.retryAfterSeconds());
            return new CreateResult(null, CommentStatus.APPROVED, clock.instant(), null);
        }

        UUID parentId = command.parentId();
        if (parentId != null && comments.findByIdAndArticleIdAndStatusAndParentIdIsNull(
                parentId, target.articleId(), CommentStatus.APPROVED).isEmpty()) {
            throw new ProblemException("COMMENT_PARENT_INVALID", "Invalid parent comment",
                    "Replies must target an approved top-level comment on the same article");
        }
        byte[] fingerprint = security.contentFingerprint(target.articleId(), normalizeForFingerprint(content));
        if (comments.existsByArticleIdAndContentFingerprint(target.articleId(), fingerprint)) {
            throw new ProblemException("COMMENT_DUPLICATE", "Duplicate comment",
                    "An equivalent comment has already been submitted");
        }
        RateDecision rate = rate(visitorCookie, remoteAddress);
        if (!rate.allowed()) throw new CommentRateLimitException(rate.retryAfterSeconds());

        Instant now = clock.instant();
        UUID commentId = io.haoblog.shared.id.UuidV7.generate();
        String deleteToken = security.newDeleteToken();
        CommentSecurityService.EmailCiphertext encryptedEmail = security.encryptEmail(commentId, email);
        Comment comment = new Comment(commentId, target.articleId(), parentId, nickname,
                encryptedEmail == null ? null : encryptedEmail.ciphertext(),
                encryptedEmail == null ? null : encryptedEmail.nonce(),
                encryptedEmail == null ? null : encryptedEmail.keyVersion(), content,
                security.dailyIpHmac(remoteAddress, LocalDate.ofInstant(now, ZoneOffset.UTC)),
                LocalDate.ofInstant(now, ZoneOffset.UTC), fingerprint,
                security.deleteTokenDigest(deleteToken), now);
        comments.save(comment);
        outbox.save(new OutboxEvent(commentId, "COMMENT_CREATED", Map.of(
                "commentId", commentId.toString(),
                "articleId", target.articleId().toString(),
                "eventType", "COMMENT_CREATED",
                "occurredAt", now.toString()), now, now));
        return new CreateResult(commentId, comment.getStatus(), now, deleteToken);
    }

    @Transactional
    public void delete(UUID commentId, String deleteToken) {
        Comment comment = comments.findById(commentId).orElseThrow(() ->
                new ProblemException("COMMENT_NOT_FOUND", "Comment not found", "The requested comment does not exist"));
        if (comment.getStatus() == CommentStatus.USER_DELETED) {
            throw new ProblemException("COMMENT_ALREADY_DELETED", "Comment already deleted", "The comment has already been deleted");
        }
        if (deleteToken == null || deleteToken.isBlank()
                || !java.security.MessageDigest.isEqual(comment.getDeleteTokenDigest(), security.deleteTokenDigest(deleteToken))) {
            throw new ProblemException("COMMENT_DELETE_TOKEN_INVALID", "Invalid delete token", "The delete token is invalid");
        }
        comment.userDelete(clock.instant());
    }

    private RateDecision rate(String visitorCookie, String remoteAddress) {
        String source = validVisitorCookie(visitorCookie)
                ? "visitor:" + visitorCookie
                : "ip:" + Base64.getUrlEncoder().withoutPadding().encodeToString(
                        security.dailyIpHmac(remoteAddress, LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC)));
        CommentRateLimiter.Decision decision = rateLimiter.checkAndRecord(source);
        return new RateDecision(decision.allowed(), decision.retryAfterSeconds());
    }

    private ArticleCommentLookup.Target publicArticle(String slug) {
        return articles.findPublicCommentTarget(slug).orElseThrow(() ->
                new ProblemException("ARTICLE_NOT_FOUND", "Article not found", "The requested public article does not exist"));
    }

    private static boolean validVisitorCookie(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{43}");
    }

    private static String requiredTrimmed(String value, String code, String detail) {
        String normalized = value == null ? "" : value.strip();
        if (normalized.isEmpty()) throw new ProblemException(code, "Invalid comment", detail);
        return normalized;
    }

    private static void validateLengths(String nickname, String content, String email) {
        checkCharacters(nickname, 2, 40, "COMMENT_NICKNAME_INVALID", "Nickname must be 2 to 40 characters");
        checkCharacters(content, 2, 2000, "COMMENT_CONTENT_INVALID", "Content must be 2 to 2000 characters");
        if (email != null && email.codePointCount(0, email.length()) > 254) {
            throw new ProblemException("COMMENT_EMAIL_INVALID", "Invalid comment email", "Email must be at most 254 characters");
        }
    }

    private static void checkCharacters(String value, int min, int max, String code, String detail) {
        int length = value.codePointCount(0, value.length());
        if (length < min || length > max) throw new ProblemException(code, "Invalid comment", detail);
    }

    private static void validateContent(String content) {
        if (RAW_HTML.matcher(content).find() || content.indexOf('\u0000') >= 0) {
            throw new ProblemException("COMMENT_CONTENT_INVALID", "Invalid comment content", "Raw HTML is not allowed");
        }
        Matcher unsafe = UNSAFE_SCHEME.matcher(content);
        if (unsafe.find()) {
            throw new ProblemException("COMMENT_LINK_PROTOCOL_INVALID", "Invalid comment link",
                    "Only https links are allowed");
        }
        Matcher schemes = URL_SCHEME.matcher(content);
        while (schemes.find()) {
            if (!"https".equalsIgnoreCase(schemes.group(1))) {
                throw new ProblemException("COMMENT_LINK_PROTOCOL_INVALID", "Invalid comment link",
                        "Only https links are allowed");
            }
        }
        Matcher links = HTTPS_LINK.matcher(content);
        int count = 0;
        while (links.find() && ++count <= 3) { /* 统计安全链接数量 */ }
        if (count > 3) {
            throw new ProblemException("COMMENT_TOO_MANY_LINKS", "Too many comment links",
                    "At most three https links are allowed");
        }
    }

    private static String normalizeForFingerprint(String content) {
        return Normalizer.normalize(content, Normalizer.Form.NFC)
                .replaceAll("\\s+", " ").strip().toLowerCase(Locale.ROOT);
    }

    private static CommentView view(Comment comment, List<Comment> replies) {
        return new CommentView(comment.getId(), comment.getNickname(), comment.getContent(), comment.getCreatedAt(),
                replies.stream().map(reply -> new CommentView(reply.getId(), reply.getNickname(), reply.getContent(),
                        reply.getCreatedAt(), List.of())).toList());
    }

    public record CreateCommand(String nickname, String email, String content, UUID parentId,
                                String challenge, String honeypot, String website) {}

    public record CreateResult(UUID id, CommentStatus status, Instant createdAt, String deleteToken) {}

    public record CommentPage(List<CommentView> items, int page, int size, long total) {}

    public record CommentView(UUID id, String nickname, String content, Instant createdAt,
                              List<CommentView> replies) {}

    public record AdminComment(UUID id, UUID articleId, UUID parentId, String nickname, String content,
                               String emailMasked, CommentStatus status, UUID moderatorId,
                               String moderationReason, Instant moderatedAt, Instant createdAt,
                               Instant updatedAt, long version) {
        static AdminComment from(Comment comment, String emailMasked) {
            return new AdminComment(comment.getId(), comment.getArticleId(), comment.getParentId(), comment.getNickname(),
                    comment.getContent(), emailMasked, comment.getStatus(), comment.getModeratorId(),
                    comment.getModerationReason(), comment.getModeratedAt(), comment.getCreatedAt(),
                    comment.getUpdatedAt(), comment.getVersion());
        }
    }

    public record AdminCommentDetail(UUID id, UUID articleId, UUID parentId, String nickname, String content,
                                     String email, CommentStatus status, UUID moderatorId,
                                     String moderationReason, Instant moderatedAt, Instant createdAt,
                                     Instant updatedAt, long version) {
        static AdminCommentDetail from(Comment comment, String email) {
            return new AdminCommentDetail(comment.getId(), comment.getArticleId(), comment.getParentId(),
                    comment.getNickname(), comment.getContent(), email, comment.getStatus(), comment.getModeratorId(),
                    comment.getModerationReason(), comment.getModeratedAt(), comment.getCreatedAt(),
                    comment.getUpdatedAt(), comment.getVersion());
        }
    }

    private record RateDecision(boolean allowed, long retryAfterSeconds) {}
}
