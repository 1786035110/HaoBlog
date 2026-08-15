package io.haoblog.content.application;

import io.haoblog.content.persistence.ArticleRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ArticleSlugPolicyTest {
    @Test
    void normalizesAndRejectsRepositoryDuplicate() {
        ArticleRepository repository = mock(ArticleRepository.class);
        UUID articleId = UUID.randomUUID();
        when(repository.existsBySlugAndIdNot("same-slug", articleId)).thenReturn(true);
        ArticleSlugPolicy policy = new ArticleSlugPolicy(repository);

        assertThrows(DuplicateArticleSlugException.class,
                () -> policy.normalizeAndCheck(" SAME  SLUG ", articleId));
        when(repository.existsBySlugAndIdNot("new-slug", articleId)).thenReturn(false);
        assertEquals("new-slug", policy.normalizeAndCheck(" NEW_SLUG ", articleId));
    }
}
