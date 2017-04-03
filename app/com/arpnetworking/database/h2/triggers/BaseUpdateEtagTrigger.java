/**
 * Copyright 2016 Groupon.com
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
package com.arpnetworking.database.h2.triggers;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import models.ebean.Organization;
import org.h2.api.Trigger;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Consumer;

/**
 * Base class for H2 triggers for updating etags.
 *
 * @author Deepika Misra (deepika at groupon dot com)
 */
public abstract class BaseUpdateEtagTrigger implements Trigger {

    /**
     * Public constructor.
     *
     * @param incrementEtag Method to update the etag.
     * @param orgIndex The index to find the organization for which to update the etag.
     */
    public BaseUpdateEtagTrigger(final Consumer<Organization> incrementEtag, final int orgIndex) {
        _incrementEtag = incrementEtag;
        _orgIndex = orgIndex;
    }

    @Override
    public void init(
            final Connection conn,
            final String schemaName,
            final String triggerName,
            final String tableName,
            final boolean before,
            final int type) throws SQLException {}

    @Override
    @SuppressFBWarnings("SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING")
    public void fire(final Connection conn, final Object[] oldRow, final Object[] newRow) throws SQLException {
        final long orgId = (long) (oldRow != null ? oldRow[_orgIndex] : newRow[_orgIndex]);
        final Organization org = Organization.refById(orgId);
        _incrementEtag.accept(org);
    }

    @Override
    public void close() {
        // ignore
    }

    @Override
    public void remove() {
        // ignore
    }

    private final Consumer<Organization> _incrementEtag;
    private final int _orgIndex;
}
