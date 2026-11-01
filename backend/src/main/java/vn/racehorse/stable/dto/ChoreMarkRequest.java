package vn.racehorse.stable.dto;

/** Body of a mark; the path form needs none, `date` defaults to today and any other day is refused. */
public record ChoreMarkRequest(String date) {
}
