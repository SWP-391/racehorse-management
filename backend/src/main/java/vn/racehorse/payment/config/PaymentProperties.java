package vn.racehorse.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bank details shown in the payment QR and the key of the bank-transfer notice (TECH-234, TECH-236). The key is a
 * secret: never log it or send it back. {@code simulated} marks every transfer received while the service is in
 * its test mode (BR-226, TECH-235).
 */
@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(String webhookApiKey, String bankCode, String accountNumber, String accountName,
                                String qrBaseUrl, boolean simulated) {

    public String qrBaseUrlOrDefault() {
        return qrBaseUrl == null || qrBaseUrl.isBlank() ? "https://vietqr.app/img" : qrBaseUrl;
    }

    public boolean keyConfigured() {
        return webhookApiKey != null && !webhookApiKey.isBlank();
    }
}
