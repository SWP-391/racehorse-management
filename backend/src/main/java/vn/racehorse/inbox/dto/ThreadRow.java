package vn.racehorse.inbox.dto;

import java.time.Instant;

/** One chat with one person (or with the Club Manager role): every conversation with them, newest activity first. */
public record ThreadRow(String key, String name, String role, String preview, Instant lastAt, long unread,
                        int topics, int openTopics, boolean hasFeedback) {
}
