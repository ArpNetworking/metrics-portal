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

import {NotificationGroup} from './NotificationGroup';
import PaginatedSearchableList = require('../PaginatedSearchableList');
import $ = require('jquery');

class NotificationGroupsList extends PaginatedSearchableList<NotificationGroup> {
    fetchData(query, callback) {
        $.getJSON("v1/notificationgroup/query", query, (data) => {
            const alertsList: NotificationGroup[] = data.data.map(
                (v: NotificationGroup) => new NotificationGroup(v.id, v.name, v.entries));
            callback(alertsList, data.pagination);
        });
    }
}

class NotificationGroupsViewModel {
    notificationGroups: NotificationGroupsList = new NotificationGroupsList();
    deletingId: string = null;
    remove: (alert: NotificationGroup) => void;

    constructor() {
        this.notificationGroups.query();
        this.remove = (alert: NotificationGroup) => {
            this.deletingId = alert.id;
            $("#confirm-delete-modal").modal('show');
        };
    }

    confirmDelete() {
        $.ajax({
            type: "DELETE",
            url: "/v1/notificationgroup/" + this.deletingId,
            contentType: "application/json"
        }).done(() => {
            $("#confirm-delete-modal").modal('hide');
            this.notificationGroups.query();
            this.deletingId = null;
        });
    }
}

export = NotificationGroupsViewModel;
