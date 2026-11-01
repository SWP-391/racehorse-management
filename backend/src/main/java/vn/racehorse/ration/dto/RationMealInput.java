package vn.racehorse.ration.dto;

import java.util.List;

public record RationMealInput(String name, String time, List<RationItemInput> items) {
}
