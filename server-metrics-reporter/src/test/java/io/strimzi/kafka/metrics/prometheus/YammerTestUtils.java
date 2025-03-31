/*
 * Copyright Strimzi authors.
 * License: Apache License 2.0 (see the file LICENSE or http://apache.org/licenses/LICENSE-2.0.html).
 */
package io.strimzi.kafka.metrics.prometheus;

import java.util.function.Supplier;

public class YammerTestUtils {

    /**
     * Create a new Yammer metric
     * @param valueSupplier The supplier providing the value of the metric
     * @return The Yammer metric
     */
    public static <T> com.yammer.metrics.core.Gauge<T> newYammerMetric(Supplier<T> valueSupplier) {
        return new com.yammer.metrics.core.Gauge<>() {
            @Override
            public T value() {
                return valueSupplier.get();
            }
        };
    }
}
