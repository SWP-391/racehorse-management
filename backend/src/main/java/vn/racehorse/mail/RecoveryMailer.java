package vn.racehorse.mail;

import vn.racehorse.account.EmailAddress;
import vn.racehorse.security.recovery.RecoveryPurpose;

/**
 * Sends a code to the address of an account (BR-217, BR-237): the recovery code of a forgotten password or the
 * activation code of an invited account. One interface so another mail service can replace the Brevo sender without
 * touching the flows (TECH-226). May throw a RuntimeException: the caller logs it and never lets it reach the answer
 * of the request (BR-214).
 */
public interface RecoveryMailer {

    void send(EmailAddress to, String code, RecoveryPurpose purpose);

    default void send(EmailAddress to, String code) {
        send(to, code, RecoveryPurpose.RESET);
    }
}
