package com.claudeofduty.core.notify;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The notification channels the system knows and what kind of address each
 * one delivers to. A new channel is registered here and gets a
 * {@link NotificationChannel} implementation and a queue.
 */
public final class ChannelDirectory {

    public enum AddressType {
        PHONE, EMAIL, USER
    }

    private final Map<String, AddressType> addressTypes = new ConcurrentHashMap<>();

    public static ChannelDirectory withDefaults() {
        ChannelDirectory channels = new ChannelDirectory();
        channels.register("sms", AddressType.PHONE);
        channels.register("voice", AddressType.PHONE);
        channels.register("email", AddressType.EMAIL);
        channels.register("ui", AddressType.USER);
        channels.register("push", AddressType.USER);
        return channels;
    }

    public void register(String channel, AddressType addressType) {
        addressTypes.put(channel, addressType);
    }

    public Optional<AddressType> addressType(String channel) {
        return Optional.ofNullable(channel).map(addressTypes::get);
    }
}
