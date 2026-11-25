package dev.flowtail.content;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.flowtail.content.SelfTestReport.Status;
import org.junit.jupiter.api.Test;

class SelfTestReportTest {
    @Test
    void emptyRunIsNotGreen() {
        SelfTestReport report = new SelfTestReport();
        assertFalse(report.allPassed());
        assertEquals("SELFTEST NOTHING RAN: 0 passed, 0 failed, 0 skipped", report.summary());
    }

    @Test
    void skipsNeverCountAsPasses() {
        SelfTestReport report = new SelfTestReport();
        report.skip("reload", "chunk unload is deferred");
        assertFalse(report.allPassed());
        report.pass("place", "ok");
        assertTrue(report.allPassed());
        assertEquals("SELFTEST PASSED: 1 passed, 0 failed, 1 skipped", report.summary());
    }

    @Test
    void oneFailureFailsTheRun() {
        SelfTestReport report = new SelfTestReport();
        report.pass("a", "");
        report.check(false, "b", "expected 1 got 2");
        assertFalse(report.allPassed());
        assertTrue(report.summary().startsWith("SELFTEST FAILED: 1 passed, 1 failed"));
        assertEquals(Status.FAIL, report.results().get(1).status());
        assertEquals("FAIL  b  -  expected 1 got 2", report.results().get(1).line());
        assertEquals("PASS  a", report.results().get(0).line());
    }

    @Test
    void thrownErrorsBecomeFailuresWithTheMessage() {
        SelfTestReport report = new SelfTestReport();
        report.error("x", new IllegalStateException("boom"));
        assertEquals("FAIL  x  -  threw IllegalStateException: boom", report.results().get(0).line());
    }
}
