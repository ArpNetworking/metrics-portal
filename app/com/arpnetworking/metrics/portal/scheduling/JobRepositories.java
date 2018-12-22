/*
 * Copyright 2018 Dropbox, Inc.
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
package com.arpnetworking.metrics.portal.scheduling;

import java.io.Serializable;

/**
 * todo: doc.
 *
 * @author Spencer Pearson
 */
public enum JobRepositories implements Serializable {
    /**
     * todo: a doc.
     */
    REPORTS(com.arpnetworking.metrics.portal.reports.JobRepository.class);

    /**
     * Private constructor.
     *
     * @param repoClass
     */
    JobRepositories(final Class<? extends JobRepository> repoClass) {
        this._repoClass = repoClass;
    }

    private Class<? extends JobRepository> _repoClass;
}
