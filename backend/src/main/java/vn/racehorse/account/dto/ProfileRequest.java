package vn.racehorse.account.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Name and phone of the own profile. An email in the body is read only to be refused (BR-212, TECH-F8).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProfileRequest(String displayName, String phone, @JsonProperty("email") String email) {
}
