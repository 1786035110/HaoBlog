package io.haoblog.content.application;

import io.haoblog.content.domain.ArticleStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleWorkflowServiceTest {
    @Test
    void stateMachineMatchesDocumentedTransitions() {
        assertTrue(ArticleWorkflowService.isAllowedTransition(ArticleStatus.DRAFT, ArticleStatus.SCHEDULED));
        assertTrue(ArticleWorkflowService.isAllowedTransition(ArticleStatus.DRAFT, ArticleStatus.PUBLISHED));
        assertTrue(ArticleWorkflowService.isAllowedTransition(ArticleStatus.SCHEDULED, ArticleStatus.DRAFT));
        assertTrue(ArticleWorkflowService.isAllowedTransition(ArticleStatus.SCHEDULED, ArticleStatus.PUBLISHED));
        assertTrue(ArticleWorkflowService.isAllowedTransition(ArticleStatus.PUBLISHED, ArticleStatus.PUBLISHED));
        assertTrue(ArticleWorkflowService.isAllowedTransition(ArticleStatus.PUBLISHED, ArticleStatus.SCHEDULED));
        assertTrue(ArticleWorkflowService.isAllowedTransition(ArticleStatus.PUBLISHED, ArticleStatus.ARCHIVED));
        assertTrue(ArticleWorkflowService.isAllowedTransition(ArticleStatus.ARCHIVED, ArticleStatus.DRAFT));

        assertFalse(ArticleWorkflowService.isAllowedTransition(ArticleStatus.DRAFT, ArticleStatus.ARCHIVED));
        assertFalse(ArticleWorkflowService.isAllowedTransition(ArticleStatus.SCHEDULED, ArticleStatus.ARCHIVED));
        assertFalse(ArticleWorkflowService.isAllowedTransition(ArticleStatus.ARCHIVED, ArticleStatus.PUBLISHED));
        assertFalse(ArticleWorkflowService.isAllowedTransition(ArticleStatus.ARCHIVED, ArticleStatus.ARCHIVED));
        assertEquals(8, java.util.Arrays.stream(ArticleStatus.values())
                .flatMap(from -> java.util.Arrays.stream(ArticleStatus.values()).filter(to -> ArticleWorkflowService.isAllowedTransition(from, to)))
                .count());
    }
}
