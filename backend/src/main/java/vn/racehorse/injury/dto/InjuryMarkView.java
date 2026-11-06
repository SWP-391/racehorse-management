package vn.racehorse.injury.dto;

import vn.racehorse.injury.entity.InjuryMark;
import vn.racehorse.injury.entity.InjuryMarkEvent;
import vn.racehorse.veterinary.entity.Examination;

import java.util.List;

public record InjuryMarkView(InjuryMark mark, Examination examination, List<InjuryMarkEvent> history) {
}
