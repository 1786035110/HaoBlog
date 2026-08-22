package io.haoblog.media.application;

import java.util.UUID;

/** 由 content 模块实现，避免 media 直接依赖文章仓储和实体。 */
public interface MediaReferenceQuery {
    boolean isReferenced(UUID mediaId, String publicUrl);
}
