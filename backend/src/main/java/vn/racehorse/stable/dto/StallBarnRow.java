package vn.racehorse.stable.dto;

import vn.racehorse.horse.entity.Horse;

public record StallBarnRow(Horse horse, int choresDone, int choresTotal) {
}
