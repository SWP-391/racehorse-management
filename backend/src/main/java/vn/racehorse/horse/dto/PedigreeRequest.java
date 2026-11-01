package vn.racehorse.horse.dto;

import java.util.List;

public record PedigreeRequest(List<HorseAncestorInput> ancestors) {
}
