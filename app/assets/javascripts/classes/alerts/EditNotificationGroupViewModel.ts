/*
 * Copyright 2018 Smartsheet.com
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

import {EmailRecipient, NotificationGroup, Recipient} from './NotificationGroup';
import ko = require('knockout');
import $ = require('jquery');
import uuid = require('../Uuid');
import csrf from '../Csrf'


interface ResponseCallback {
    (response: any[]): void;
}
class EditNotificationGroupViewModel {
    id = ko.observable<string>("");
    name = ko.observable<string>("");
    recipients = ko.observableArray<Recipient>();
    addType = ko.observable<string>("email");
    addAddress = ko.observable<string>();
    initialCreate: boolean;
    addRecipientTemplate = ko.computed<string>(() => {
        return "template-add-recipient-" + this.addType();
    });

    activate(id: string) {
        if (id != null) {
            this.loadNotificationGroup(id);
            this.initialCreate = false;
        } else {
            this.id(uuid.v4());
            this.initialCreate = true;
        }
    }

    loadNotificationGroup(id: string): void {
        $.getJSON("/v1/notificationgroup/" + id, {}, (data: NotificationGroup) => {
            this.id(data.id);
            this.name(data.name);
            this.recipients(data.entries);
        });
    }

    save(): void {
        this.persistNotificationGroup()
            .done(() => {
                window.location.href = "/#notificationgroups";
            });
    }

    persistNotificationGroup(): JQuery.jqXHR {
        return $.ajax({
            type: "PUT",
            url: "/v1/notificationgroup",
            beforeSend: function(request) {
                request.setRequestHeader("Csrf-Token", csrf.getToken());
            },
            contentType: "application/json",
            dataType: "json",
            data: JSON.stringify({
                "id": this.id(),
                "name": this.name()
            }),
        })
    }

    cancel(): void {
        window.location.href = "/#notificationgroups";
    }

    recipientTemplate(recipient: Recipient): string {
        return "template-recipient-" + recipient.type;
    }

    newRecipient(): void {
        let recipient;

        recipient = new EmailRecipient();
        recipient.address = this.addAddress();
        let saveRecipient = () => {$.ajax({
            type: "PUT",
            url: "/v1/notificationgroup/" + this.id() + "/recipient",
            beforeSend: function(request) {
                request.setRequestHeader("Csrf-Token", csrf.getToken());
            },
            contentType: "application/json",
            "dataType": "json",
            data: JSON.stringify(recipient)
        }).done(() => {
            this.loadNotificationGroup(this.id());
            this.addAddress("");
        })};

        if (this.initialCreate) {
            this.persistNotificationGroup()
                .done(saveRecipient);
        } else {
            saveRecipient();
        }

    }

    removeRecipient(recipient: Recipient): void {
        $.ajax({
            type: "DELETE",
            url: "/v1/notificationgroup/" + this.id() + "/recipient",
            beforeSend: function(request) {
                request.setRequestHeader("Csrf-Token", csrf.getToken());
            },
            contentType: "application/json",
            "dataType": "json",
            data: JSON.stringify(recipient)
        }).done(() => {
            this.loadNotificationGroup(this.id());
            this.addAddress("");
        });
    }
}

export = EditNotificationGroupViewModel;
