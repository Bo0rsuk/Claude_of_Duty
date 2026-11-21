package com.claudeofduty.lambda.channels;

import com.claudeofduty.core.model.NotificationJob;
import com.claudeofduty.core.notify.NotificationChannel;
import java.util.Map;
import software.amazon.awssdk.services.sesv2.SesV2Client;

/** Plain-text email through Amazon SES. */
public final class EmailChannel implements NotificationChannel {

    private static final String CHARSET = "UTF-8";

    private final SesV2Client ses;
    private final String sender;

    public EmailChannel(SesV2Client ses, String sender) {
        this.ses = ses;
        this.sender = sender;
    }

    @Override
    public Map<String, Object> send(NotificationJob job) {
        if (sender == null || sender.isBlank()) {
            throw new IllegalStateException("No sender address is configured (notification_sender_email)");
        }
        String messageId = ses.sendEmail(request -> request
                        .fromEmailAddress(sender)
                        .destination(destination -> destination.toAddresses(job.recipient().address()))
                        .content(content -> content.simple(message -> message
                                .subject(subject -> subject.data(job.subject()).charset(CHARSET))
                                .body(body -> body.text(text -> text.data(job.body()).charset(CHARSET))))))
                .messageId();
        return Map.of("messageId", messageId);
    }
}
