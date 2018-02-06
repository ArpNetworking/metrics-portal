MQL
===

MQL is a language to query and transform time series data. The syntax is loosely based on SQL and supports a logical 
flow and transformation of data through the use of the `|` character.

In MQL, `select` statements are chained to `aggregators` to transform the raw data into something useful. The result 
of an MQL query is the result of the last statement.

Select
------

The `select` portion of the statement is constructed as follows

  (from *start_time* to *end_time*)?  
  select *metric_name*  
  (where *key1* = '*value1*'(, *key2* = '*value2*')*)?  
  (group by *key1* (, *key2*) *)?
  
Ex:  `from '1 hour ago' to 'now' select cpu/idle where cluster = 'mportal' group by host`  
This will select a 1 hour window of the cpu/idle metric from the 'mportal' cluster, with a series for each host found.

Aggregators
-----------

Aggregators are an essential part of a query.  They allow you to take a "raw" histogram stored in the time series
database and turn it into a meaningful time series that can be graphed.  The most simple of the aggregators take no
arguments and compute a statistic from the histogram.  For example, `sum` and `avg`.

`select cpu/idle group by cluster | avg`


Aggregators can also have parameters and decorate responses with additional data, as is the case with the alerting 
threshold aggregator.

`select cpu/idle group by cluster | avg | threshold operator=LESS_THAN threshold=30`

In this case, there is a threshold applied so that alerts will fire if the value (the average of the cpu/idle metric) 
is less than 30.

Combining Output
----------------

You may want to combine the results of multiple queries in an aggregator.  This is done by naming the statement with 
the `as` keyword.

ex: `select cpu/idle as idle`

The named result can then be used as input to an aggregator with the `of` keyword.

ex: `select cpu/user as u | select cpu/system as s | union of u, s`

This results in all the referenced results being fed into the aggregator and creating a single result from it.
An aggregator's results may also be named and used later.

### List of Aggregators
* [min](#agg_min)
* [max](#agg_max)
* [merge](#agg_merge)
* [percentile](#agg_percentile)
* [count](#agg_count)
* [avg](#agg_avg)
* [sum](#agg_sum)
* [diff](#agg_diff)
* [union](#agg_union)
* [threshold](#agg_threshold)
* [top](#agg_top)
* [bottom](#agg_bottom)


### <a name="agg_min"></a>min Aggregator

The min aggregator takes the minimum value in the bucket.

### <a name="agg_max"></a>max Aggregator

The max aggregator takes the maximum value in the bucket.

### <a name="agg_merge"></a>merge Aggregator

Merges histograms within the bucket, produces a single, combined histogram.

### <a name="agg_percentile"></a>percentile Aggregator

Computes the specified percentile from the histogram.  
args: `percentile` - a number between 0.0 and 1 indicating the percentile to use. required. ex: 0.99 = 99%

### <a name="agg_count"></a>count Aggregator

The count aggregator returns the number of samples recorded in the bucket.

### <a name="agg_avg"></a>avg Aggregator

The avg aggregator returns the average (mean) of the samples recorded in the bucket.

### <a name="agg_sum"></a>sum Aggregator

The sum aggregator returns the sum of the samples recorded in the bucket.

### <a name="agg_diff"></a>diff Aggregator

The diff aggregator computes the difference in successive data points in a time series.  This **_does not_** work with 
histograms.

### <a name="agg_union"></a>union Aggregator

The union aggregator combines the results of multiple, named queries into a single result with multiple time series.
This is often used to feed the results into an aggregator expecting a single input.

### <a name="agg_top"></a>top Aggregator

The top aggregator selects the "top" series in each input result as defined by summing each datapoint in the series and
taking the largest.  
args: `count` - the number of series to keep. defaults to 1

### <a name="agg_bottom"></a>bottom Aggregator

The bottom aggregator selects the "bottom" series in each input result as defined by summing each datapoint in the 
series and taking the smallest.  
args: `count` - the number of series to keep. defaults to 1

### <a name="agg_threshold"></a>threshold Aggregator

Performs a basic threshold comparison on each time series. Decorates the data with alert events.  
args:
* `threshold` - the value to compare to. required.
* `operator` - how to compare the value. required. must be GREATER_THAN, GREATER_THAN_OR_EQUAL_TO, 
        LESS_THAN, LESS_THAN_OR_EQUAL, EQUAL_TO, or NOT_EQUAL_TO
* `dwellPeriod` - period the series must match before an alert event is created. ISO8601 period format. 
defaults to PT0S (0 seconds)
* `recoveryPeriod` - period the series must not match before an alert event is considered cleared. ISO8601 period 
format. defaults to PT0S (0 seconds)
