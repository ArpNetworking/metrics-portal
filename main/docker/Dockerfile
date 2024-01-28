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

FROM eclipse-temurin:17-jre

MAINTAINER inscopemetrics.io

EXPOSE 8080
WORKDIR /opt/metrics-portal

# Configuration
#
# There are two groups of arguments:
#   1) JVM (e.g. JVM_XMS, JAVA_OPTS, ADDITIONAL_JAVA_OPTS)
#   2) Application (e.g. APP_OPTS, ADDITIONAL_APP_OPTS)
#
# Each group has three flavors of arguments:
#   1) Explicit (e.g. JVM_XMS)
#   2) Default (e.g. JAVA_OPTS)
#   3) Additional (e.g. ADDITIONAL_JAVA_OPTS)
#
# The explicit arguments are commonly modified and should be used for these
# parameters instead of relying on default or additional arguments to set them.
#
# The default arguments are less common and rarely need to be modified. When
# you override the default you override all the defaults. You should not use
# defaults to change explicit arguments.
#
# The additional arguments let you add to and in some cases override default
# arguments without specifying all default arguments.
#
# Implementation note: the $JAVA_OPTS setting is added by automatically by the
# run script which is why you don't see it passed explicitly.
#
# Implementation note: when passing arguments with evaluated contents the
# result of the evaluation is not quoted because it cannot be determined
# whether the intention was to result in one or many arguments. Therefore, when
# using evaluated arguments always perform the quoting (if any) yourself.

ENV JVM_XMS="64m"
ENV JVM_XMX="1024m"
ENV LOGBACK_CONFIG="-Dlogger.file=/opt/metrics-portal/config/logback.xml"
ENV METRICS_PORTAL_CONFIG="-Dconfig.resource=portal.application.conf"
ENV JAVA_OPTS="\
    --add-opens java.base/java.nio=ALL-UNNAMED \
    --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
    --add-opens=java.base/java.lang=ALL-UNNAMED \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=/opt/metrics-portal/logs/metrics-portal.oom.hprof \
    -Xlog:gc*:file=/opt/metrics-portal/logs/metrics-portal.gc.log:none:filecount=5,filesize=10M \
    -XX:+UseStringDeduplication \
    -XX:+UseG1GC \
    -Duser.timezone=UTC"
ENV ADDITIONAL_JAVA_OPTS=""
ENV APP_OPTS="/opt/metrics-portal"
ENV ADDITIONAL_APP_OPTS=""

# Build
RUN apt update && DEBIAN_FRONTEND=noninteractive apt install -y gosu chromium-browser && rm -rf /var/lib/apt/lists/*
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
    "--add-opens=java.base/java.nio=ALL-UNNAMED", \
    "--add-opens java.base/sun.nio.ch=ALL-UNNAMED", \
    "${LOGBACK_CONFIG}", \
    "${METRICS_PORTAL_CONFIG}", \
    "-Xms${JVM_XMS}", \
    "-Xmx${JVM_XMX}", \
    "${ADDITIONAL_JAVA_OPTS}", \
    "--", \
    "${APP_OPTS}", \
    "${ADDITIONAL_APP_OPTS}"]
