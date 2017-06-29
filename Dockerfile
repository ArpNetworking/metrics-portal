# Copyright 2016 Smartsheet.com
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

FROM java:8u111-jre-alpine
RUN apk --update add bash
EXPOSE 9000
RUN mkdir -p /opt/metrics-portal/logs
WORKDIR /opt/metrics-portal
ENV CONFIG_FILE -Dconfig.resource=portal.application.conf
ENV PARAMS $CONFIG_FILE
CMD /opt/metrics-portal/bin/metrics-portal $PARAMS
ADD target/universal/stage/bin /opt/metrics-portal/bin
ADD target/universal/stage/conf /opt/metrics-portal/conf
ADD target/universal/stage/lib /opt/metrics-portal/lib
