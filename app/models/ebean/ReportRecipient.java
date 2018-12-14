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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity
@Table(name = "report_recipients", schema = "portal")
public class ReportRecipient {
    public enum RecipientType {
        EMAIL
    }

    private ReportRecipient(RecipientType type, String recipient) {
        this.type = type;
        this.recipient = recipient;
    }

    public static ReportRecipient newEmailRecipient(String recipient) {
        return new ReportRecipient(RecipientType.EMAIL, recipient);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "recipient_group_id")
    private ReportRecipientGroup recipientGroup;

    @Column(name = "recipient")
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private RecipientType type;

    public Long getId() {
        return id;
    }

    public String get() {
        return recipient;
    }

    public RecipientType getType() {
        return type;
    }
}
