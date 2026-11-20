package com.claudeofduty.lambda;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;

/**
 * Consumes notification jobs from the channel queues. Not implemented yet: it
 * will deliver them through the channel implementations (task B9).
 */
public class NotifierHandler implements RequestHandler<SQSEvent, Void> {

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        context.getLogger().log("Received " + event.getRecords().size() + " jobs, delivery is not implemented yet");
        return null;
    }
}
