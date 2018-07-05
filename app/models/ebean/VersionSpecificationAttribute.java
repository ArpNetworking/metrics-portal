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
package models.ebean;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

/**
 * Data model for <code>VersionSpecificationAttribute</code>s (for Evergreen). Holds a simple key-value String pair, intended to
 * be compared to key-value pairs provided by a host.
 *
 * @see VersionSpecification
 * @author Matthew Hayter (mhayter at groupon dot com)
 */
// CHECKSTYLE.OFF: MemberNameCheck
@Entity
@Table(name = "version_specification_attributes", schema = "portal")
public class VersionSpecificationAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "keyName")
    private String key;

    @Column(name = "value")
    private String attributeValue;

    @ManyToOne
    @JoinColumn(name = "version_specification")
    private VersionSpecification versionSpecification;

    public Long getId() {
        return id;
    }

    public void setId(final Long value) {
        id = value;
    }

    public String getKey() {
        return key;
    }

    public void setKey(final String value) {
        key = value;
    }

    public String getValue() {
        return attributeValue;
    }

    public void setValue(final String value) {
        attributeValue = value;
    }

    public VersionSpecification getVersionSpecification() {
        return versionSpecification;
    }

    public void setVersionSpecification(final VersionSpecification value) {
        versionSpecification = value;
    }
}
// CHECKSTYLE.ON: MemberNameCheck
