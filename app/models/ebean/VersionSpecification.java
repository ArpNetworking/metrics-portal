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
 * Data model for Version Specifications (for Evergreen).
 *
 * A VersionSpecification models a 'rule' by which a host making a query to Evergreen may be matched to a VersionSet
 * for purposes of specifying the correct packages and versions that should be installed on the host. The VersionSpecifications
 * form a total order via the `next` attribute (i.e. ordered via the linked-list method).
 *
 * A VersionSpecification is said to match a host iff each of the VersionSpecificationAttributes match the corresponding
 * propery provided by the host.
 *
 * Currently, there is no support for domains or separate groups of VersionSpecifications.
 *
 * @author Matthew Hayter (mhayter at groupon dot com)
 */
// CHECKSTYLE.OFF: MemberNameCheck
// CHECKSTYLE.OFF: HiddenFieldCheck
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

    public void setId(final Long id) {
        this.id = id;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(final UUID uuid) {
        this.uuid = uuid;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(final Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(final Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public VersionSpecification getNext() {
        return next;
    }

    public void setNext(final VersionSpecification next) {
        this.next = next;
    }

    public VersionSet getVersionSet() {
        return versionSet;
    }

    public void setVersionSet(final VersionSet versionSet) {
        this.versionSet = versionSet;
    }

    public List<VersionSpecificationAttribute> getVersionSpecificationAttributes() {
        return versionSpecificationAttributes;
    }

    public void setVersionSpecificationAttributes(final List<VersionSpecificationAttribute> versionSpecificationAttributes) {
        this.versionSpecificationAttributes = versionSpecificationAttributes;
    }
}
// CHECKSTYLE.ON
