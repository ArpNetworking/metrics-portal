/*
 * Copyright 2015 Groupon.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package global;

import com.arpnetworking.metrics.MetricsFactory;
import com.arpnetworking.play.metrics.MetricsActionWrapper;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import jakarta.inject.Inject;
import play.mvc.Action;
import play.mvc.Http;

import java.lang.reflect.Method;

/**
 * Request handler for the application.
 *
 * @author Brandon Arp (brandon dot arp at inscopemetrics dot io)
 */
@SuppressFBWarnings("NM_SAME_SIMPLE_NAME_AS_INTERFACE")
public class ActionCreator implements play.http.ActionCreator {
    /**
     * Public constructor.
     *
     * @param metricsFactory The metrics factory
     */
    @Inject
    public ActionCreator(final MetricsFactory metricsFactory) {
        _metricsFactory = metricsFactory;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Action<?> createAction(final Http.Request request, final Method method) {
        return new MetricsActionWrapper(_metricsFactory);
    }

    private final MetricsFactory _metricsFactory;
}
