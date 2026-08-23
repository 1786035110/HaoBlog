package io.haoblog.comment.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "haoblog.comment.notification")
public class CommentNotificationProperties {
    private boolean enabled;
    private String recipient = "";
    private String from = "";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getRecipient() { return recipient; }
    public void setRecipient(String recipient) { this.recipient = recipient; }
    public String getFrom() { return from; }
    public void setFrom(String from) { this.from = from; }
}
