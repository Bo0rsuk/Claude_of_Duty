package com.claudeofduty.lambda.channels;

import com.claudeofduty.core.model.NotificationJob;
import com.claudeofduty.core.notify.NotificationChannel;
import java.util.Map;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;

/** Text messages through Amazon SNS. */
public final class SmsChannel implements NotificationChannel {

    /** Transactional messages get delivery priority over promotional ones. */
    private static final Map<String, MessageAttributeValue> ATTRIBUTES = Map.of("AWS.SNS.SMS.SMSType",
            MessageAttributeValue.builder().dataType("String").stringValue("Transactional").build());

    private final SnsClient sns;

    public SmsChannel(SnsClient sns) {
        this.sns = sns;
    }

    @Override
    public Map<String, Object> send(NotificationJob job) {
        String messageId = sns.publish(request -> request
                        .phoneNumber(job.recipient().address())
                        .message(job.body())
                        .messageAttributes(ATTRIBUTES))
                .messageId();
        return Map.of("messageId", messageId);
    }
}
