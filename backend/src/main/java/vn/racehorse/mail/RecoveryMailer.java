package vn.racehorse.mail;

import vn.racehorse.account.EmailAddress;

/**
 * Sends the recovery code to the address of an account (BR-217). One interface so another mail service can replace
 * the Brevo sender without touching the recovery flow (TECH-226). May throw a RuntimeException: the caller logs it
 * and never lets it reach the answer of the request (BR-214).
 */
public interface RecoveryMailer {

    void send(EmailAddress to, String code);
}
