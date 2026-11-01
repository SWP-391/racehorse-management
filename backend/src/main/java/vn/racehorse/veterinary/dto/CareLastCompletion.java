package vn.racehorse.veterinary.dto;

import java.time.LocalDate;

/** The latest completion of an item, and whether anything beyond it (notes, batch, override, postponement) exists. */
public record CareLastCompletion(LocalDate dateDone, String product, String performer) {
}
