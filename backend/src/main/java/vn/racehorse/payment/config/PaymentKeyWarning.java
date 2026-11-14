package vn.racehorse.payment.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Says once at start that no transfer notice will be accepted until the key is set (fail closed, TECH-234). */
@Component
class PaymentKeyWarning {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentKeyWarning.class);

    private final PaymentProperties props;

    PaymentKeyWarning(PaymentProperties props) {
        this.props = props;
    }

    @PostConstruct
    void warn() {
        if (!props.keyConfigured()) {
            LOG.warn("SEPAY_WEBHOOK_API_KEY is not set: every bank-transfer notice will be refused.");
        }
    }
}
