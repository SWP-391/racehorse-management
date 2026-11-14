package vn.racehorse.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Sender of the recovery mail and the key of the mail service (TECH-226). The key is a secret: never log it. */
@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(String from, String fromName, String brevoApiKey, String brevoUrl) {

    public String fromNameOrDefault() {
        return fromName == null || fromName.isBlank() ? "Racehorse" : fromName;
    }

    public String urlOrDefault() {
        return brevoUrl == null || brevoUrl.isBlank() ? "https://api.brevo.com/v3/smtp/email" : brevoUrl;
    }

    public boolean configured() {
        return brevoApiKey != null && !brevoApiKey.isBlank() && from != null && !from.isBlank();
    }
}
