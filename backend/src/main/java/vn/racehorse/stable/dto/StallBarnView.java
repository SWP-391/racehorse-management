package vn.racehorse.stable.dto;

import java.util.List;

public record StallBarnView(List<StallAreaView> areas, List<StallBarnRow> unplaced, boolean canPlace) {
}
