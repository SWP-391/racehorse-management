package vn.racehorse.inbox.dto;

import java.util.List;

/** A whole chat: who it is with, its conversations (oldest first) and all messages in time order. */
public record ThreadDetail(String key, String name, String role, List<ThreadTopic> topics, List<ThreadMessage> messages,
                           Long openTopicId) {
}
