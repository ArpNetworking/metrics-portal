# Copyright 2019 Dropbox, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

FROM openjdk:8u151-jre-alpine

MAINTAINER arpnetworking

EXPOSE 8080
WORKDIR /opt/metrics-portal

# Configuration
ENV JVM_XMS="64m"
ENV JVM_XMX="1024m"
ENV LOGBACK_CONFIG="-Dlogback.configurationFile=/opt/metrics-portal/config/logback.xml"
ENV METRICS_PORTAL_CONFIG="-Dconfig.resource=portal.application.conf"
ENV JAVA_OPTS=""

# Build
RUN mkdir -p /opt/metrics-portal/lib/ext && \
    mkdir -p /opt/metrics-portal/config && \
    mkdir -p /opt/metrics-portal/logs
ADD deps /opt/metrics-portal/lib/
ADD bin /opt/metrics-portal/bin/
ADD config /opt/metrics-portal/config/
ADD lib /opt/metrics-portal/lib/

# Entry point
ENTRYPOINT [ \
    "/opt/metrics-portal/bin/metrics-portal", \
    "${LOGBACK_CONFIG}", \
    "${METRICS_PORTAL_CONFIG}", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/opt/metrics-portal/logs/metrics-portal.oom.hprof", \
    "-XX:+PrintGCDetails", \
    "-XX:+PrintGCDateStamps", \
    "-Xloggc:/opt/metrics-portal/logs/metrics-portal.gc.log", \
    "-XX:NumberOfGCLogFiles=2", \
    "-XX:GCLogFileSize=50M", \
    "-XX:+UseGCLogFileRotation", \
    "-Xms${JVM_XMS}", \
    "-Xmx${JVM_XMX}", \
    "-XX:+UseStringDeduplication", \
    "-XX:+UseG1GC", \
    "-Duser.timezone=UTC", \
    "--", \
    "/opt/metrics-portal" ]
