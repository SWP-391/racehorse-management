package vn.racehorse.ration.dto;

import java.util.List;

public record TemplateRequest(String name, String tag, List<RationMealInput> meals) {
}
