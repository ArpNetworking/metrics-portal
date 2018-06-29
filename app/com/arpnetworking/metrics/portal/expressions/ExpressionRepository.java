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
package com.arpnetworking.metrics.portal.expressions;

import models.internal.Expression;
import models.internal.ExpressionQuery;
import models.internal.Organization;
import models.internal.QueryResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Interface for repository of expressions.
 *
 * @author Ville Koskela (ville dot koskela at inscopemetrics dot com)
 */
public interface ExpressionRepository {

    /**
     * Open the <code>ExpressionRepository</code>.
     */
    void open();

    /**
     * Close the <code>ExpressionRepository</code>.
     */
    void close();

    /**
     * Get the <code>Expression</code> by identifier.
     *
     * @param identifier The <code>Expression</code> identifier.
     * @param organization The organization owning the expression.
     * @return The matching <code>Expression</code> if found or <code>Optional.empty()</code>.
     */
    Optional<Expression> get(UUID identifier, Organization organization);

    /**
     * Create a query against the expressions repository.
     *
     * @param organization Organization to search in.
     * @return Instance of <code>ExpressionQuery</code>.
     */
    ExpressionQuery createQuery(Organization organization);

    /**
     * Query expressions.
     *
     * @param query Instance of <code>ExpressionQuery</code>.
     * @return The <code>Collection</code> of all expressions.
     */
    QueryResult<Expression> query(ExpressionQuery query);

    /**
     * Retrieve the total number of expressions in the repository.
     *
     * @param organization The organization owning the expressions.
     * @return The total number of expressions.
     */
    long getExpressionCount(Organization organization);

    /**
     * Add a new expression or update an existing one in the repository.
     *
     * @param expression The expression to add to the repository.
     * @param organization The organization owning the expression.
     */
    void addOrUpdateExpression(Expression expression, Organization organization);
}
