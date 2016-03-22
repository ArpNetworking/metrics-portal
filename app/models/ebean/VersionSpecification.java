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
package models.ebean;

import com.avaje.ebean.annotation.CreatedTimestamp;
import com.avaje.ebean.annotation.UpdatedTimestamp;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.OneToOne;
import javax.persistence.Table;

/**
 * Data model for <code>VersionSpecification</code>s (for Evergreen).
 *
 * A <code>VersionSpecification</code> models a 'rule' by which a host making a query to Evergreen may be matched to a VersionSet
 * for purposes of specifying the correct packages-versions that should be installed on the host. The <code>VersionSpecification</code>s
 * form a total order via the `next` attribute (i.e. ordered via the linked-list method).
 *
 * A <code>VersionSpecification</code> is said to match a host if and only if each of the
 * <code>VersionSpecificationAttribute</code>s match the corresponding qproperty provided by the host.
 *
 * Currently, there is no support for domains or separate groups of <code>VersionSpecification</code>s.
 *
 * @author Matthew Hayter (mhayter at groupon dot com)
 */
// CHECKSTYLE.OFF: MemberNameCheck
@Entity
@Table(name = "version_specifications", schema = "portal")
public class VersionSpecification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "uuid")
    private UUID uuid;

    @CreatedTimestamp
    @Column(name = "created_at")
    private Timestamp createdAt;

    @UpdatedTimestamp
    @Column(name = "updated_at")
    private Timestamp updatedAt;

    @OneToOne
    @JoinColumn(name = "next")
    private VersionSpecification next;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "version_set_id")
    private VersionSet versionSet;

    @OneToMany(mappedBy = "versionSpecification")
    private List<VersionSpecificationAttribute> versionSpecificationAttributes;

    public Long getId() {
        return id;
    }

    public void setId(final Long value) {
        id = value;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(final UUID value) {
        uuid = value;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final Timestamp value) {
        createdAt = value;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(final Timestamp value) {
        updatedAt = value;
    }

    public VersionSpecification getNext() {
        return next;
    }

    public void setNext(final VersionSpecification value) {
        next = value;
    }

    public VersionSet getVersionSet() {
        return versionSet;
    }

    public void setVersionSet(final VersionSet value) {
        versionSet = value;
    }

    public List<VersionSpecificationAttribute> getVersionSpecificationAttributes() {
        return versionSpecificationAttributes;
    }

    public void setVersionSpecificationAttributes(final List<VersionSpecificationAttribute> value) {
        versionSpecificationAttributes = value;
    }
}
// CHECKSTYLE.ON: MemberNameCheck
