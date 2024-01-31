[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)
[![Twitter Follow](https://img.shields.io/twitter/follow/strimziio?style=social)](https://twitter.com/strimziio)

# Strimzi Prometheus Metrics Reporter

This repository contains the _Prometheus Metrics Reporter for Apache Kafka server and client components_ as proposed in [Strimzi Proposal #64](https://github.com/strimzi/proposals/blob/main/064-prometheus-metrics-reporter.md).
The implementation is currently still in progress.

## Build

```shell
mvn package assembly:single
```

## Run

### Kafka Brokers
Add the following to your broker configuration:
```properties
metric.reporters=io.strimzi.kafka.metrics.KafkaPrometheusMetricsReporter
kafka.metrics.reporters=io.strimzi.kafka.metrics.YammerPrometheusMetricsReporter
```

### Kafka Clients
Add the following to your client configuration:
```properties
metric.reporters=io.strimzi.kafka.metrics.KafkaPrometheusMetricsReporter
```

## Access Metrics

Metrics are exposed on `http://localhost:8080/metrics`.

## Getting help

If you encounter any issues while using Strimzi, you can get help using:

- [#strimzi channel on CNCF Slack](https://slack.cncf.io/)
- [Strimzi Users mailing list](https://lists.cncf.io/g/cncf-strimzi-users/topics)
- [GitHub Discussions](https://github.com/orgs/strimzi/discussions)

## Strimzi Community Meetings

You can join our regular community meetings:
* Thursday 8:00 AM UTC (every 4 weeks starting from 4th June 2020) - [convert to your timezone](https://www.thetimezoneconverter.com/?t=8%3A00&tz=UTC)
* Thursday 4:00 PM UTC (every 4 weeks starting from 18th June 2020) - [convert to your timezone](https://www.thetimezoneconverter.com/?t=16%3A00&tz=UTC)

Resources:
* [Meeting minutes, agenda and Zoom link](https://docs.google.com/document/d/1V1lMeMwn6d2x1LKxyydhjo2c_IFANveelLD880A6bYc/edit#heading=h.vgkvn1hr5uor)
* [Recordings](https://youtube.com/playlist?list=PLpI4X8PMthYfONZopcRd4X_stq1C14Rtn)
* [Calendar](https://calendar.google.com/calendar/embed?src=c_m9pusj5ce1b4hr8c92hsq50i00%40group.calendar.google.com) ([Subscribe to the calendar](https://calendar.google.com/calendar/u/0?cid=Y19tOXB1c2o1Y2UxYjRocjhjOTJoc3E1MGkwMEBncm91cC5jYWxlbmRhci5nb29nbGUuY29t))

## Contributing

You can contribute by:
- Raising any issues you find using Strimzi
- Fixing issues by opening Pull Requests
- Improving documentation
- Talking about Strimzi

All bugs, tasks or enhancements are tracked as [GitHub issues](https://github.com/strimzi/metrics-reporter/issues).

If you want to get in touch with us first before contributing, you can use:

- [#strimzi channel on CNCF Slack](https://slack.cncf.io/)
- [Strimzi Dev mailing list](https://lists.cncf.io/g/cncf-strimzi-dev/topics)
- [GitHub Discussions](https://github.com/orgs/strimzi/discussions)

## License
Strimzi is licensed under the [Apache License](./LICENSE), Version 2.0