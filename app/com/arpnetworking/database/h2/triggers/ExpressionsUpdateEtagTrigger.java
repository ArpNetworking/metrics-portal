/*
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

import org.h2.api.Trigger;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Trigger to update Etag after every insert, delete or update statement.
 *
 * TODO(ville): Delete this class when we deprecate H2.
 *
 * @author Deepika Misra (deepika at groupon dot com)
 */
public class ExpressionsUpdateEtagTrigger implements Trigger {

    /**
     * Public no args constructor.
     */
    public ExpressionsUpdateEtagTrigger() { }

    @Override
    public void init(
            final Connection conn,
            final String schemaName,
            final String triggerName,
            final String tableName,
            final boolean before,
            final int type) throws SQLException {
        // Don't do anything; expressions have been deleted
    }

    @Override
    public void fire(
            final Connection conn,
            final Object[] oldRow,
            final Object[] newRow) throws SQLException {
        // Don't do anything; expressions have been deleted
    }

    @Override
    public void close() throws SQLException {
        // Don't do anything; expressions have been deleted
    }

    @Override
    public void remove() throws SQLException {
        // Don't do anything; expressions have been deleted
    }
}
