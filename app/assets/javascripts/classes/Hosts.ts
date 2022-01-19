/*
 * Copyright 2014 Groupon.com
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

import Color from './Color';
import ConnectionVM from './ConnectionVM';
import * as ko from 'knockout';
import graphViewModel from "./GraphViewModel";

module Hosts {
    export var connections: ko.ObservableArray<ConnectionVM> = ko.observableArray<ConnectionVM>();
    export var connectionIndex: { [id: string]: ConnectionVM; } = {};
    export var colors: Color[] = [
        Color.of('#e31a1c'),
        Color.of('#1f78b4'),
        Color.of('#33a02c'),
        Color.of('#6a3d9a'),
        Color.of('#fdbf6f'),
        Color.of('#b15928'),
        Color.of('#ff7f00'),
        Color.of('#cab2d6'),
        Color.of('#fb9a99'),
        Color.of('#a6cee3'),
        Color.of('#40c9ff'),
        Color.of('#b2df8a'),
        Color.of('#8dd3c7'),
        Color.of('#ffffb3'),
        Color.of('#bebada'),
        Color.of('#fb8072'),
        Color.of('#80b1d3'),
        Color.of('#fdb462'),
        Color.of('#b3de69'),
        Color.of('#fccde5'),
        Color.of('#d9d9d9'),
        Color.of('#bc80bd'),
        Color.of('#ccebc5'),
        Color.of('#ffed6f')
    ];

    export var colorId = 0;

//    console.log("Hosts construct GVM = ", GraphViewModel);

    export var connectToServer = (server: string) => {
        //check to make sure the server is not already in the connect list
        for (var i = 0; i < connections().length; i++) {
            var c = connections()[i];
            if (c.server == server) {
                return;
            }
        }

        var connectionNode = new ConnectionVM(server);
        connectionNode.colorBase(getColor());
        connectionIndex[server] = connectionNode;
        connections.push(connectionNode);
        connectionNode.connect();

    };

    export var removeConnection = (cvm: ConnectionVM) => {
        connections.remove(cvm);
        delete connectionIndex[cvm.server];
        graphViewModel.disconnect(cvm);
        cvm.close();
    };

    export var getColor = (): Color => {
        var color = colors[colorId % colors.length];
        colorId++;
        return color;
    };
}

export default Hosts;
