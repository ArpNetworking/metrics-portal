///<amd-dependency path="jquery"/>
///<amd-dependency path="jquery.ui"/>
///<amd-dependency path="jqrangeslider"/>
///<amd-dependency path="typeahead" />
///<amd-dependency path="daterangepicker" />

import ko = require('knockout');
import $ = require('jquery');
import _ = require('underscore');
import * as moment from 'moment/moment'

module kobindings {
    ko.bindingHandlers['slider'] = {
        update: function(element, valueAccessor) {
            // First get the latest data that we're bound to
            var value = valueAccessor();
            var valueUnwrapped: any = ko.utils.unwrapObservable(value);
            $(element).rangeSlider(valueUnwrapped);
            $(element).bind("valuesChanging", function (e, data) {
                valueUnwrapped.slide(e, data);
            });
        }
    };

    ko.bindingHandlers['slide'] = {
        update: function(element, valueAccessor, allBindingsAccessor) {
            var shouldShow = ko.utils.unwrapObservable(valueAccessor());
            var bindings = allBindingsAccessor();
            var direction = ko.utils.unwrapObservable(bindings.direction);
            var duration = ko.utils.unwrapObservable<number>(bindings.duration) || 400;
            var after: Function = ko.utils.unwrapObservable<Function>(bindings.after);

            var effectOptions = { "direction": direction };

            if (shouldShow) {
                after();
                $(element).show("slide", effectOptions, duration);
            } else {
                $(element).hide("slide", effectOptions, duration, after);
            }
        }
    };

    ko.bindingHandlers['tooltip'] = {
        update: function(element, valueAccessor) {
            var value = valueAccessor();
            var valueUnwrapped = ko.utils.unwrapObservable(value);
            //TODO: tooltip this
        }
    };

    ko.bindingHandlers['stackdrag'] = {
        update: function(element, valueAccessor) {
            var thisLevel = $(element).parent().children();
            var value = valueAccessor();
            var valueUnwrapped = ko.utils.unwrapObservable(value);

            $.each(thisLevel, function(index, e) { $(e).draggable(valueUnwrapped); });
        }
    };

    ko.bindingHandlers['legendBlock'] = {
        update: function(element, valueAccessor) {
            // First get the latest data that we're bound to
            var value = valueAccessor();

            // Next, whether or not the supplied model property is observable, get its current value
            var valueUnwrapped = ko.utils.unwrapObservable(value);

            var context = element.getContext('2d');
            context.clearRect(0, 0, element.width, element.height);
            context.beginPath();
            context.rect(3, 3, element.width - 6, element.height - 6);
            context.fillStyle = valueUnwrapped;
            context.fill();
            context.lineWidth = 2;
            context.strokeStyle = '#F0F0F0';
            context.stroke();
        }
    };

    ko.bindingHandlers["typeahead"] = {
        init: function(element, valueAccessor, allValuesAccessor) {
            let value = valueAccessor();
            let valueUnwrapped: any = ko.utils.unwrapObservable(value);

            let ta = $(element).typeahead(valueUnwrapped.options.opt, valueUnwrapped.options.source);
            let strict = false;
            if (valueUnwrapped && valueUnwrapped.options && valueUnwrapped.options.opt && valueUnwrapped.options.opt.strict) {
                strict = true;
            }

            if (valueUnwrapped.value !== undefined) {
                if (!strict) {
                    ta.data().ttTypeahead.input.onSync("queryChanged", () => {
                        valueUnwrapped.value(ta.val());
                    });
                } else {
                    ta.on('blur', (event) => {
                        console.log("blur time", event, ta.val(), valueUnwrapped.value());
                        if (ta.typeahead('val') != displayFn(valueUnwrapped.value())) {
                            valueUnwrapped.value(null);
                        }
                    });
                }

                ta.on('typeahead:autocompleted', (event, object, dataset) => {
                    valueUnwrapped.value(object);
                });

                ta.on('typeahead:selected', (event, object, dataset) => {
                    valueUnwrapped.value(object);
                });

                let displayFn = (obj) => {return obj.value;};
                if (valueUnwrapped && valueUnwrapped.options && valueUnwrapped.options.source && valueUnwrapped.options.source.display) {
                    displayFn = _.isFunction(valueUnwrapped.options.source.display) ?
                        valueUnwrapped.options.source.display : (obj) => { return obj[valueUnwrapped.options.source.display]; };
                }
                //Hack to handle the clearing of the query
                valueUnwrapped.value.subscribe((newValue) => {
                    if (newValue == null) {
                        ta.typeahead('val', '');
                    } else if (ta.typeahead('val') != displayFn(newValue)) {
                        ta.typeahead('val', displayFn(newValue));
                    }
                });
                if (valueUnwrapped.value()) {
                    ta.typeahead('val', displayFn(valueUnwrapped.value()));
                }
            }
        }
    };

    ko.bindingHandlers['dateRange'] = {
        init: function(element, valueAccessor) {
            let el: any = $(element);
            let value = valueAccessor();
            let valueUnwrapped: any = ko.utils.unwrapObservable(value);
            let ranges = {
                   "Last hour" : [() => moment().subtract(1, 'hour'), () => moment()],
                   "Last 2 hours" : [() => moment().subtract(2, 'hour'), () => moment()],
                   "Last 3 hours" : [() => moment().subtract(3, 'hour'), () => moment()],
                   "Last 6 hours" : [() => moment().subtract(6, 'hour'), () => moment()],
                   "Today": [() => moment().startOf('day'), () => moment().endOf('day')]
               };

            let defaultOptions = {
               ranges: _.mapObject(ranges, (o) => [o[0](), o[1]()]),
               autoApply: true
            };

            let options = Object.assign({}, defaultOptions, valueUnwrapped.options || {});
            let range = el.daterangepicker(options, (start, end, label) => {
                if (label == "Custom Range") {
                    valueUnwrapped.target([start, end]);
                } else if (ranges[label]) {
                    let range = ranges[label];
                    valueUnwrapped.target([range[0](), range[1]()]);
                }
            });

        }
    }
}

export = kobindings;
