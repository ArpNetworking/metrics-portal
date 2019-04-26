///<amd-dependency path="jquery"/>
///<amd-dependency path="jquery.ui"/>
///<amd-dependency path="jqrangeslider"/>
///<amd-dependency path="typeahead" />
///<amd-dependency path="datetimepicker" />

import ko = require('knockout');
import $ = require('jquery');
import _ = require('underscore');
import bootstrap = require('bootstrap');

import * as moment from 'moment-timezone/moment-timezone';

// This const is necessary for typescript to see bootstrap as a 'used' import and not remove it as dead code
const _bootstrap = bootstrap;

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

    ko.bindingHandlers['popover'] = {
        init: function(element, valueAccessor) {
            const value = valueAccessor();
            const valueUnwrapped = ko.unwrap(value);

            let options;
            if (valueUnwrapped === Object(valueUnwrapped)) {
                // It's an options map
                if (valueUnwrapped.hasOwnProperty('content')) {
                    options = {content: valueUnwrapped.content};
                } else if (valueUnwrapped.hasOwnProperty('id')) {
                    const id = ko.unwrap(valueUnwrapped.id);
                    const selector = `.popover-content[data-id="${id}"]`;
                    options = {
                        content: $(selector).html(),
                    }
                }
            } else {
                // Bind the top level value as the content
                options = {content: valueUnwrapped};
            }
            $(element).popover(options);
        }
    };

    // Adapted from the installation page of datetimepicker:
    // https://eonasdan.github.io/bootstrap-datetimepicker/Installing/#knockout
    ko.bindingHandlers['datetimepicker'] = {
        init: function (element, valueAccessor, allBindingsAccessor) {
            //initialize datepicker with some optional options
            const options = allBindingsAccessor().dateTimePickerOptions || {};

            const $element: any = $(element);
            $element.datetimepicker(options);

            //when a user changes the date, update the view model
            ko.utils.registerEventHandler(element, "dp.change", function (event) {
                const value = valueAccessor();
                if (ko.isObservable(value)) {
                    if (event.date && !(event.date instanceof moment)) {
                        value(moment.utc(event.date));
                    } else {
                        value(event.date);
                    }
                }
            });

            ko.utils.domNodeDisposal.addDisposeCallback(element, function () {
                const picker = $(element).data("DateTimePicker");
                if (picker) {
                    picker.destroy();
                }
            });
        },
        update: function (element, valueAccessor) {
            const picker = $(element).data("DateTimePicker");
            //when the view model is updated, update the widget
            let koMoment: moment.Moment = ko.utils.unwrapObservable(valueAccessor());
            if (picker && koMoment) {
                picker.date(koMoment.toDate());
            }
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
    }
}

export = kobindings;
