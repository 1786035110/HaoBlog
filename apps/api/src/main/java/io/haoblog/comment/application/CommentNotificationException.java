package io.haoblog.comment.application;

public class CommentNotificationException extends RuntimeException {
    public CommentNotificationException() {
        super("Comment notification could not be sent");
    }
}
