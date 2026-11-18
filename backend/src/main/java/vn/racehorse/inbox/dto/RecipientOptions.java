package vn.racehorse.inbox.dto;

import java.util.List;

/** What the new-message form offers: the horses the caller may name and the recipients for the chosen horse. */
public record RecipientOptions(List<HorseOption> horses, List<Recipient> recipients) {
}
