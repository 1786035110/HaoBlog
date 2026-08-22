package io.haoblog.content.application;

import java.util.Optional;
import java.util.UUID;

/** 为 comment 模块公开文章身份和评论开关，不暴露 content 实体或仓储。 */
public interface ArticleCommentLookup {
    Optional<Target> findPublicCommentTarget(String slug);

    record Target(UUID articleId, boolean commentsEnabled) {}
}
