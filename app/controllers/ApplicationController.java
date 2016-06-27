/**
 * Copyright 2016 Inscope Metrics, Inc.
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
package controllers;

import models.internal.Features;
import play.libs.F;
import play.mvc.Controller;
import play.mvc.Result;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Metrics portal application Play controller.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
@Singleton
public final class ApplicationController extends Controller {

    /**
     * Public constructor.
     *
     * @param features The <code>Features</code> instance.
     */
    @Inject
    public ApplicationController(final Features features) {
        _features = features;
    }

    /**
     * Render the header view.
     *
     * @return Rendered header view.
     */
    public F.Promise<Result> getHeaderViewModel() {
        return F.Promise.pure(ok(views.html.HeaderViewModel.render(_features)));
    }

    private final Features _features;
}
