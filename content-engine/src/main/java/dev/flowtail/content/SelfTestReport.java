package dev.flowtail.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects the outcome of the in-server self-test ({@code /content selftest}). Pure logic with no Bukkit types.
 *
 * <p>Three outcomes only: PASS, FAIL, and SKIP (the check could not run here, with the reason). A SKIP is never
 * counted as a pass, so a run full of skips cannot look green.
 */
final class SelfTestReport {
    enum Status { PASS, FAIL, SKIP }

    record Result(Status status, String name, String detail) {
        String line() { return status + "  " + name + (detail.isEmpty() ? "" : "  -  " + detail); }
    }

    private final List<Result> results = new ArrayList<>();

    void pass(String name, String detail) { results.add(new Result(Status.PASS, name, detail)); }
    void fail(String name, String detail) { results.add(new Result(Status.FAIL, name, detail)); }
    void skip(String name, String reason) { results.add(new Result(Status.SKIP, name, reason)); }

    /** PASS when {@code ok}, FAIL otherwise; {@code detail} is shown either way. */
    void check(boolean ok, String name, String detail) { if (ok) pass(name, detail); else fail(name, detail); }

    /** A check that threw: always a FAIL, with the exception type and message so the user can paste it. */
    void error(String name, Throwable error) {
        fail(name, "threw " + error.getClass().getSimpleName() + ": " + error.getMessage());
    }

    List<Result> results() { return Collections.unmodifiableList(results); }
    int count(Status status) { return (int) results.stream().filter(r -> r.status() == status).count(); }
    boolean allPassed() { return count(Status.FAIL) == 0 && count(Status.PASS) > 0; }

    /** One line a user can paste back: {@code SELFTEST FAILED: 14 passed, 1 failed, 2 skipped}. */
    String summary() {
        String verdict = count(Status.FAIL) > 0 ? "FAILED" : count(Status.PASS) == 0 ? "NOTHING RAN" : "PASSED";
        return "SELFTEST " + verdict + ": " + count(Status.PASS) + " passed, " + count(Status.FAIL) + " failed, "
                + count(Status.SKIP) + " skipped";
    }
}
