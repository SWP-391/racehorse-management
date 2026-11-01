package vn.racehorse.veterinary.dto;

import vn.racehorse.horse.entity.Horse;
import vn.racehorse.veterinary.entity.CareItem;
import vn.racehorse.veterinary.service.CareService.DueState;

public record CareDueView(CareItem item, Horse horse, DueState state) {
}
