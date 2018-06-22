/**
 * Copyright 2016 Smartsheet.com
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
package models.ebean;

import io.ebean.Ebean;
import io.ebean.Finder;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * Model class to represent expression etag records.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
// CHECKSTYLE.OFF: MemberNameCheck
@Entity
@Table(name = "expressions_etags", schema = "portal")
public class ExpressionEtags {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "organization")
    private Organization organization;

    @Column(nullable = false)
    private long etag;

    /**
     * Increments an etag record or creates one if it does not exist.
     *
     * @param organization the organization
     */
    public static void incrementEtag(final Organization organization) {
        ExpressionEtags etag = FINDER.query()
                .setForUpdate(true)
                .where()
                .eq("organization", organization)
                .findOne();
        if (etag == null) {
            etag = new ExpressionEtags();
            etag.setOrganization(organization);
            etag.setEtag(1);
        } else {
            etag.setEtag(etag.getEtag() + 1);
        }
        Ebean.save(etag);
    }

    /**
     * Looks up an etag value for an organization.
     *
     * @param organization the organization
     * @return the etag value, or 0 if a value does not exist in the table
     */
    public static long getEtagByOrganization(final models.internal.Organization organization) {
        final ExpressionEtags etag = FINDER.query()
                .where()
                .eq("organization.uuid", organization.getId())
                .findOne();
        if (etag != null) {
            return etag.getEtag();
        }
        return 0;
    }

    public Long getId() {
        return id;
    }

    public void setId(final Long value) {
        id = value;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(final Organization value) {
        organization = value;
    }

    public long getEtag() {
        return etag;
    }

    public void setEtag(final long value) {
        etag = value;
    }

    private static final Finder<Long, ExpressionEtags> FINDER = new Finder<>(ExpressionEtags.class);
}
// CHECKSTYLE.ON: MemberNameCheck
