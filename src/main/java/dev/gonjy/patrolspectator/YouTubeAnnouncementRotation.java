package dev.gonjy.patrolspectator;

import java.util.LinkedHashSet;
import java.util.List;

/** Rotates distinct guidance messages so consecutive automated posts do not repeat. */
final class YouTubeAnnouncementRotation {
    private int nextIndex;

    synchronized String next(List<String> configuredMessages) {
        if (configuredMessages == null || configuredMessages.isEmpty()) return null;
        List<String> messages = configuredMessages.stream()
                .filter(message -> message != null && !message.isBlank())
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new), List::copyOf));
        if (messages.isEmpty()) return null;
        String selected = messages.get(nextIndex % messages.size());
        nextIndex = (nextIndex + 1) % messages.size();
        return selected;
    }
}
