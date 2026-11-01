package vn.racehorse.training.dto;

import vn.racehorse.account.entity.UserAccount;

import java.time.LocalTime;
import java.util.List;

public record SessionAssignable(List<UserAccount> available, List<SessionBlocked> unavailable, LocalTime nextFree) {
}
