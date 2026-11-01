package vn.racehorse.training.dto;

import vn.racehorse.account.entity.UserAccount;
import vn.racehorse.training.service.Unavailable;

public record SessionBlocked(UserAccount groom, Unavailable why) {
}
