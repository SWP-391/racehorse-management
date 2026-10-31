package vn.racehorse.common.exception;

import java.util.Map;

/** Error body returned for every refused request. */
public record ApiError(String code, String message, Map<String, String> fields) {
}
