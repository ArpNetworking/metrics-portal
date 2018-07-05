/*
 * Copyright 2017 Smartsheet.com
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

import play.api.http.EnabledFilters;
import play.filters.cors.CORSFilter;
import play.filters.gzip.GzipFilter;
import play.http.DefaultHttpFilters;
import play.mvc.EssentialFilter;

import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Creates the set of filters used for handling requests.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
@Singleton
public final class Filters extends DefaultHttpFilters {
    /**
     * Public constructor.
     *
     * @param enabledFilters Framework provided enabled filters.
     * @param corsFilter The CORS filter
     * @param gzipFilter The GZip filter
     */
    @Inject
    public Filters(final EnabledFilters enabledFilters, final CORSFilter corsFilter, final GzipFilter gzipFilter) {
        // Prepend the CORS filter, append the GZip filter.
        // NOTE: CORS must go first so that we can whitelist API endpoints from requiring a CSRF token
        super(append(prepend(enabledFilters.asJava().getFilters(), corsFilter.asJava()), gzipFilter.asJava()));
    }

    private static List<EssentialFilter> append(final List<EssentialFilter> filters, final EssentialFilter toAppend) {
        final List<EssentialFilter> combinedFilters = new ArrayList<>(filters);
        combinedFilters.add(toAppend);
        return combinedFilters;
    }

    private static List<EssentialFilter> prepend(final List<EssentialFilter> filters, final EssentialFilter toPrepend) {
        final List<EssentialFilter> combinedFilters = new ArrayList<>(filters);
        combinedFilters.add(0, toPrepend);
        return combinedFilters;
    }
}
