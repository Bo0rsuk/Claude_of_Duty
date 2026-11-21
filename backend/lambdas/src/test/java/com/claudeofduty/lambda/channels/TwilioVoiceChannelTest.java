package com.claudeofduty.lambda.channels;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TwilioVoiceChannelTest {

    private static final String XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

    @Test
    void theCallReadsTheMessageTwiceAndTreatsItAsTextNotMarkup() {
        String script = TwilioVoiceChannel.script("payment-api is failing: 5 < 10 & rising", "");

        assertEquals(XML + "<Response><Say language=\"en-US\">payment-api is failing: 5 &lt; 10 &amp; rising</Say>"
                + "<Pause length=\"1\"/>"
                + "<Say language=\"en-US\">Again: payment-api is failing: 5 &lt; 10 &amp; rising</Say></Response>", script);
        assertTrue(TwilioVoiceChannel.script(" ", null).contains("You have a new alert."));
    }

    @Test
    void aRecordedMessageIsPlayedBeforeTheAlertIsRead() {
        String script = TwilioVoiceChannel.script("payment-api is failing", "https://squawk.com.pl/voice/alert.mp3?v=1&x=2");

        assertTrue(script.startsWith(XML + "<Response><Play>https://squawk.com.pl/voice/alert.mp3?v=1&amp;x=2</Play>"
                + "<Say language=\"en-US\">payment-api is failing</Say>"), script);
    }

    @Test
    void theRequestNamesBothNumbersAndWhereTheCallScriptIs() {
        String url = TwilioVoiceChannel.scriptUrl("https://api.example.pl/voice/twiml", "payment-api is failing & rising");
        assertEquals("https://api.example.pl/voice/twiml?text=payment-api+is+failing+%26+rising", url);

        assertEquals("To=%2B48600100200&From=%2B15005550006"
                        + "&Url=https%3A%2F%2Fapi.example.pl%2Fvoice%2Ftwiml%3Ftext%3Dpayment-api%2Bis%2Bfailing%2B%2526%2Brising",
                TwilioVoiceChannel.form("+48600100200", "+15005550006", url));
    }
}
