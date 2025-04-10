/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus.common;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AbstractReporterTest {

    @Test
    public void testAllowedMetricsNotReconfigurable() {
        Pattern pattern = Pattern.compile("pattern_.*");
        TestReporter reporter = new TestReporter(pattern, false);
        reporter.addMetric("pattern_metric", new MetricWrapper(null, null, null, null) {
            @Override
            public String prometheusName() {
                return "pattern_metric";
            }
        });
        assertEquals(1, reporter.allowedMetrics().size());

        reporter.addMetric("pattern2_metric", new MetricWrapper(null, null, null, null) {
            @Override
            public String prometheusName() {
                return "pattern2_metric";
            }
        });
        assertEquals(1, reporter.allowedMetrics().size());

        reporter.allowlist = Pattern.compile("(pattern_.*)|(pattern2_.*)");
        reporter.updateAllowedMetrics();
        assertEquals(1, reporter.allowedMetrics().size());

        reporter.removeMetric("pattern_metric");
        assertTrue(reporter.allowedMetrics().isEmpty());
    }

    @Test
    public void testAllowedMetricsReconfigurable() {
        Pattern pattern = Pattern.compile("pattern_.*");
        TestReporter reporter = new TestReporter(pattern, true);
        reporter.addMetric("pattern_metric", new MetricWrapper(null, null, null, null) {
            @Override
            public String prometheusName() {
                return "pattern_metric";
            }
        });
        assertEquals(1, reporter.allowedMetrics().size());

        reporter.addMetric("pattern2_metric", new MetricWrapper(null, null, null, null) {
            @Override
            public String prometheusName() {
                return "pattern2_metric";
            }
        });
        assertEquals(1, reporter.allowedMetrics().size());

        reporter.allowlist = Pattern.compile("(pattern_.*)|(pattern2_.*)");
        reporter.updateAllowedMetrics();
        assertEquals(2, reporter.allowedMetrics().size());

        reporter.removeMetric("pattern_metric");
        assertEquals(1, reporter.allowedMetrics().size());
        reporter.removeMetric("pattern2_metric");
        assertTrue(reporter.allowedMetrics().isEmpty());
    }

    static final class TestReporter extends AbstractReporter {

        private final boolean isReconfigurable;
        private Pattern allowlist;

        TestReporter(Pattern allowlist, boolean isReconfigurable) {
            this.allowlist = allowlist;
            this.isReconfigurable = isReconfigurable;
        }

        @Override
        protected Pattern allowlist() {
            return allowlist;
        }

        @Override
        protected boolean isReconfigurable() {
            return isReconfigurable;
        }
    }
}
