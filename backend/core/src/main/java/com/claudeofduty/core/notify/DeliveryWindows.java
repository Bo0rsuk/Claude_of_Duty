package com.claudeofduty.core.notify;

import com.claudeofduty.core.model.Rule.Delivery;
import com.claudeofduty.core.model.Rule.Window;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.regex.Pattern;

/** The hours in which a rule's routing applies. Semantics are in Docs/contracts/rules.md. */
public final class DeliveryWindows {

    private static final Pattern TIME = Pattern.compile("([01][0-9]|2[0-3]):[0-5][0-9]");

    private DeliveryWindows() {
    }

    /** @throws IllegalArgumentException with the reason, when the setting cannot be used */
    public static void validate(Delivery delivery, ChannelDirectory channels) {
        if (delivery == null) {
            return;
        }
        Window window = delivery.window();
        if (window == null) {
            throw new IllegalArgumentException("delivery.window is required when delivery is set");
        }
        if (window.from() == null || window.to() == null
                || !TIME.matcher(window.from()).matches() || !TIME.matcher(window.to()).matches()) {
            throw new IllegalArgumentException("delivery.window.from and .to must be times of day such as 08:00");
        }
        if (window.from().equals(window.to())) {
            throw new IllegalArgumentException("delivery.window.from and .to must differ");
        }
        try {
            zone(window);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException(
                    "delivery.window.timezone must be a time zone name such as Europe/Warsaw");
        }
        if (delivery.outsideWindow() != null && channels.addressType(delivery.outsideWindow()).isEmpty()) {
            throw new IllegalArgumentException("delivery.outsideWindow must be a channel: sms, voice, email, ui or push");
        }
    }

    /** @return true when the rule has a window and the moment falls outside it */
    public static boolean outside(Delivery delivery, Instant moment) {
        if (delivery == null || delivery.window() == null) {
            return false;
        }
        Window window = delivery.window();
        LocalTime time = moment.atZone(zone(window)).toLocalTime();
        LocalTime from = LocalTime.parse(window.from());
        LocalTime to = LocalTime.parse(window.to());
        // A window whose end is earlier than its start runs over midnight, e.g. 22:00 to 06:00.
        boolean inside = from.isBefore(to)
                ? !time.isBefore(from) && time.isBefore(to)
                : !time.isBefore(from) || time.isBefore(to);
        return !inside;
    }

    private static ZoneId zone(Window window) {
        return window.timezone() == null || window.timezone().isBlank() ? ZoneId.of("UTC") : ZoneId.of(window.timezone());
    }
}
