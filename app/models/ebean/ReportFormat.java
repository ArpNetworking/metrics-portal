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

import javax.persistence.DiscriminatorColumn;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;
import javax.persistence.OneToOne;
import javax.persistence.Table;

// CHECKSTYLE.OFF: MemberNameCheck

/**
 * Data model for a report format.
 *
 * This should be treated as an abstract class and never instantiated.
 *
 * @author Christian Briones (cbriones at dropbox dot com)
 */
// TODO(cbriones): Make this class abstract, when (if?) possible.
// If the abstract keyword is added, ebean does not generate cascading INSERT operations when a parent entity is added.
// This might be a bug in ebean. This is reproducible using the DatabaseReportRepository tests with ReportRecipientGroup.
@Entity
@Table(name = "report_formats", schema = "portal")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type")
public class ReportFormat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    protected Integer id;

    @OneToOne(mappedBy = "_format", fetch = FetchType.LAZY)
    private ReportRecipientAssoc _reportRecipientAssoc;

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

    /* package */ models.internal.reports.ReportFormat toInternal() {
        throw new UnsupportedOperationException("A bare report format should not be converted to an internal model.");
    }
}
// CHECKSTYLE.ON: MemberNameCheck
