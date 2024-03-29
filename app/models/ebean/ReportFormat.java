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

package models.ebean;

import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

// CHECKSTYLE.OFF: MemberNameCheck

/**
 * Data model for a report format.
 *
 * This should be treated as an abstract class and never instantiated.
 *
 * NOTE: This class is enhanced by Ebean to do things like lazy loading and
 * resolving relationships between beans. Therefore, including functionality
 * which serializes the state of the object can be dangerous (e.g. {@code toString},
 * {@code @Loggable}, etc.).
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
// TODO(cbriones): Make this class abstract, when (if?) possible.
// If the abstract keyword is added, ebean does not generate cascading INSERT operations when a parent entity is added.
// This might be a bug in ebean. This is reproducible using the DatabaseReportRepository tests.
@Entity
@Table(name = "report_formats", schema = "portal")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type")
public class ReportFormat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Integer id;

    @OneToOne(mappedBy = "format", fetch = FetchType.LAZY)
    private ReportRecipientAssoc reportRecipientAssoc;

    /**
     * Default constructor to prevent instantiation outside of subclasses.
     */
    protected ReportFormat() { }

    public Integer getId() {
        return id;
    }

    public void setId(final Integer value) {
        id = value;
    }

    /**
     * Convert this format to its internal representation.
     * @return The internal representation of this format.
     */
    /* package */ models.internal.reports.ReportFormat toInternal() {
        throw new UnsupportedOperationException("A bare report format should not be converted to an internal model.");
    }
}
// CHECKSTYLE.ON: MemberNameCheck
