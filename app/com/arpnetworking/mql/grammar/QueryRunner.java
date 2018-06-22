/**
 * Copyright 2017 Smartsheet
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
package com.arpnetworking.mql.grammar;

import com.arpnetworking.kairos.client.KairosDbClient;
import com.arpnetworking.kairos.client.models.MetricsQuery;
import com.arpnetworking.metrics.util.ImmutableCollectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import net.sf.oval.ConstraintViolation;
import net.sf.oval.context.FieldContext;
import net.sf.oval.exception.ConstraintsViolatedException;
import org.joda.time.DateTime;
import org.joda.time.DateTimeFieldType;
import org.joda.time.Duration;
import org.joda.time.Period;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import javax.inject.Inject;

/**
 * Executes a query against KairosDB.  Note: this class is stateful and not thread safe.
 *
 * @author Brandon Arp (brandon dot arp at smartsheet dot com)
 */
public class QueryRunner extends MqlBaseVisitor<Object> {
    /**
     * Public constructor.
     *
     * @param kairosDbClient client to call KairosDB
     * @param mapper ObjectMapper to form responses
     */
    @Inject
    public QueryRunner(final KairosDbClient kairosDbClient, final ObjectMapper mapper) {
        _kairosDbClient = kairosDbClient;
        _mapper = mapper;
    }

    @Override
    public CompletionStage<TimeSeriesResult> visitStatement(final MqlParser.StatementContext ctx) {
        final List<MqlParser.StageContext> stages = ctx.stage();
        // Build each stage and chain them together
        for (MqlParser.StageContext stage : stages) {
            _previousStage = visitStage(stage);
        }

        return _previousStage.execute();
    }

    @Override
    public StageExecution visitStage(final MqlParser.StageContext ctx) {
        final StageExecution execution;
        if (ctx.select() != null) {
            execution = visitSelect(ctx.select());
        } else if (ctx.agg() != null) {
            execution = visitAgg(ctx.agg());
        } else {
            execution = null;
        }
        if (ctx.timeSeriesReference() != null) {
            final String name = visitTimeSeriesReference(ctx.timeSeriesReference());
            final StageExecution previous = _stages.put(name, execution);
            if (previous != null) {
                throw new IllegalStateException("Multiple stages with name '" + name + "' found");
            }
        }

        return execution;
    }

    @Override
    public StageExecution visitAgg(final MqlParser.AggContext ctx) {
        final List<StageExecution> dependencies;
        if (ctx.ofList() != null) {
            dependencies = visitOfList(ctx.ofList());
        } else if (_previousStage != null) {
            dependencies = Collections.singletonList(_previousStage);
        } else {
            dependencies = Collections.emptyList();
        }

        final String aggregator = visitIdentifier(ctx.aggFunctionName().identifier()).toLowerCase(Locale.US);
        final Period aggPeriod;
        if (ctx.aggPeriod() != null) {
            aggPeriod = visitAggPeriod(ctx.aggPeriod());
        } else {
            aggPeriod = Period.minutes(1);
        }

        // Check to see if we can lift the aggregator to a dependent query
        if (dependencies.size() >= 1) {
            final StageExecution dependency = dependencies.get(0);
            final ImmutableMap<String, Object> args = visitAggArgList(ctx.aggArgList());
            if (dependencies.size() == 1 && dependency instanceof SelectExecution && LIFTABLE_AGGREGATIONS.contains(aggregator)) {
                //If the aggregator is liftable, create a new SelectExecution with the aggregator lifted into it
                return applyAggregator(aggregator, aggPeriod, args, (SelectExecution) dependency);
            } else {
                final ClassBoundBuilderProvider<?> provider = AGG_BUILDERS.get(aggregator);
                if (provider == null) {
                    throw new IllegalArgumentException("Unknown aggregator '" + aggregator + "'");
                }

                final BaseExecution.Builder<?, ?> builder = provider.get(args, _mapper);
                dependencies.forEach(builder::addDependency);
                try {
                    return builder.build();
                } catch (final ConstraintsViolatedException ex) {
                    final ConstraintViolation[] violations = ex.getConstraintViolations();
                    for (final ConstraintViolation violation : violations) {
                        if (violation.getContext() instanceof FieldContext) {
                            final Field field = ((FieldContext) violation.getContext()).getField();
                            throw new IllegalArgumentException(
                                    String.format(
                                            "Illegal value for field %s; %s",
                                            field.getName(),
                                            violation.getMessage()));
                        }
                    }
                    throw new IllegalArgumentException("boom!");
                }
            }
        }
        throw new IllegalStateException("Aggregator '" + aggregator + "' does not have any inputs");
    }

    private StageExecution applyAggregator(
            final String aggregator,
            final Period aggPeriod,
            final ImmutableMap<String, Object> args,
            final SelectExecution selectExecution) {
        final SelectExecution.Builder builder = SelectExecution.Builder.clone(selectExecution);

        final MetricsQuery.Aggregator.Builder aggregatorBuilder = new MetricsQuery.Aggregator.Builder()
                .setName(aggregator)
                .setOtherArgs(args);

        if (hasSampling(aggregator)) {
            final MetricsQuery.Sampling sampling = new MetricsQuery.Sampling.Builder()
                    .setPeriod(aggPeriod)
                    .build();
            aggregatorBuilder.setSampling(sampling);
        } else {
            aggregatorBuilder.setSampling(null);
        }

        final ImmutableList<MetricsQuery.Metric> newMetrics = selectExecution.getQuery().getMetrics().stream()
                .map(MetricsQuery.Metric.Builder::<MetricsQuery.Metric, MetricsQuery.Metric.Builder>clone)
                .map(b -> b.addAggregator(aggregatorBuilder.build()).build())
                .collect(ImmutableCollectors.toList());

        final MetricsQuery.Builder newQueryBuilder = MetricsQuery.Builder.clone(selectExecution.getQuery());
        newQueryBuilder.setMetrics(newMetrics);
        builder.setQuery(newQueryBuilder.build());
        return builder.build();
    }

    private boolean hasSampling(final String aggregator) {
        if (aggregator.equals("diff")) {
            return false;
        }
        return true;
    }

    @Override
    public Period visitAggPeriod(final MqlParser.AggPeriodContext ctx) {
        final int value = visitNumericLiteral(ctx.numericLiteral()).intValue();
        final TimeUnit unit = visitTimeUnit(ctx.timeUnit());
        switch (unit) {
            case SECONDS:
                return Period.seconds(value);
            case MINUTES:
                return Period.minutes(value);
            case HOURS:
                return Period.hours(value);
            case DAYS:
                return Period.days(value);
            default:
                throw new IllegalArgumentException("Unsupported time unit \"" + unit + "\"");
        }
    }

    @Override
    public ImmutableMap<String, Object> visitAggArgList(final MqlParser.AggArgListContext ctx) {
        final ImmutableMap.Builder<String, Object> argMap = ImmutableMap.builder();
        for (MqlParser.AggArgPairContext argPairContext : ctx.aggArgPair()) {
            argMap.put(visitArgName(argPairContext.argName()), visitArgValue(argPairContext.argValue()));
        }
        return argMap.build();
    }

    @Override
    public String visitArgName(final MqlParser.ArgNameContext ctx) {
        return visitIdentifier(ctx.identifier());
    }

    @Override
    public List<StageExecution> visitOfList(final MqlParser.OfListContext ctx) {
        final List<StageExecution> ofList = Lists.newArrayList();
        final List<MqlParser.TimeSeriesReferenceContext> references = ctx.timeSeriesReference();
        for (MqlParser.TimeSeriesReferenceContext reference : references) {
            final String name = visitIdentifier(reference.identifier());
            if (!_stages.containsKey(name)) {
                throw new IllegalStateException("Referenced stage '" + name + "' does not exist for aggregation");
            }
            ofList.add(_stages.get(name));
        }
        return ofList;
    }

    @Override
    public String visitTimeSeriesReference(final MqlParser.TimeSeriesReferenceContext ctx) {
        return visitIdentifier(ctx.identifier());
    }

    @Override
    public SelectExecution visitSelect(final MqlParser.SelectContext ctx) {
        final MetricsQuery.Builder query = new MetricsQuery.Builder();

        final String metricName = visitMetricName(ctx.metricName());
        if (ctx.timeRange() != null) {
            final TimeRange timeRange = visitTimeRange(ctx.timeRange());
            query.setStartTime(timeRange._start)
                    .setEndTime(timeRange._end);
            _timeRange = timeRange;
        } else if (_timeRange != null) {
            query.setStartTime(_timeRange._start)
                    .setEndTime(_timeRange._end);
        } else {
            query.setStartTime(DateTime.now().minusMinutes(60))
                    .setEndTime(DateTime.now());
        }

        final MetricsQuery.Metric.Builder metricBuilder = new MetricsQuery.Metric.Builder()
                .setName(metricName);

        if (ctx.whereClause() != null) {
            metricBuilder.setTags(visitWhereClause(ctx.whereClause()));
        }
        if (ctx.groupByClause() != null) {
            metricBuilder.setGroupBy(visitGroupByClause(ctx.groupByClause()));
        }

        query.setMetrics(ImmutableList.of(metricBuilder.build()));
        return new SelectExecution.Builder()
                .setQuery(query.build())
                .setClient(_kairosDbClient)
                .build();
    }

    @Override
    public String visitMetricName(final MqlParser.MetricNameContext ctx) {
        if (ctx.identifier() != null) {
            return visitIdentifier(ctx.identifier());
        } else {
            return visitStringLiteral(ctx.stringLiteral());
        }
    }

    @Override
    public TimeRange visitTimeRange(final MqlParser.TimeRangeContext ctx) {
        final List<MqlParser.PointInTimeContext> times = ctx.pointInTime();
        final DateTime start = visitPointInTime(times.get(0));
        final DateTime end;
        if (times.size() > 1) {
            end = visitPointInTime(times.get(1));
        } else {
            end = DateTime.now();
        }
        return new TimeRange(start, end);
    }

    @Override
    public ImmutableList<MetricsQuery.GroupBy> visitGroupByClause(final MqlParser.GroupByClauseContext ctx) {
        final MetricsQuery.GroupBy.Builder groupBy = new MetricsQuery.GroupBy.Builder();
        groupBy.setName("tag");
        final List<String> tags = Lists.newArrayList();
        for (final MqlParser.GroupByTermContext term : ctx.groupByTerm()) {
            tags.add(visitGroupByTerm(term));
        }
        groupBy.addOtherArg("tags", tags);
        return ImmutableList.of(groupBy.build());
    }

    @Override
    public String visitGroupByTerm(final MqlParser.GroupByTermContext ctx) {
        return visitIdentifier(ctx.identifier());
    }

    @Override
    public DateTime visitPointInTime(final MqlParser.PointInTimeContext ctx) {
        return (DateTime) super.visitPointInTime(ctx);
    }

    @Override
    public DateTime visitAbsoluteTime(final MqlParser.AbsoluteTimeContext ctx) {
        final String toParse = visitStringLiteral(ctx.stringLiteral());
        return DateTime.parse(toParse);
    }

    @Override
    public ImmutableMultimap<String, String> visitWhereClause(final MqlParser.WhereClauseContext ctx) {
        final ImmutableMultimap.Builder<String, String> map = ImmutableMultimap.builder();
        if (ctx != null) {
            final List<MqlParser.WhereTermContext> whereTerms = ctx.whereTerm();
            for (MqlParser.WhereTermContext where : whereTerms) {
                final List<String> values = visitWhereValue(where.whereValue());
                for (String value : values) {
                    map.put(visitTag(where.tag()), value);
                }
            }
        }

        return map.build();
    }

    @Override
    public Number visitNumericLiteral(final MqlParser.NumericLiteralContext ctx) {
        if (ctx.Double() != null) {
            return Double.parseDouble(ctx.Double().getText());
        } else {
            return Long.parseLong(ctx.Integral().getText());
        }
    }

    @Override
    public String visitTag(final MqlParser.TagContext ctx) {
        return visitIdentifier(ctx.identifier());
    }

    @Override
    public List<String> visitWhereValue(final MqlParser.WhereValueContext ctx) {
        return ctx.stringLiteral().stream().map(this::visitStringLiteral).collect(Collectors.toList());
    }

    @Override
    public String visitStringLiteral(final MqlParser.StringLiteralContext ctx) {
        final String raw = ctx.getText();
        final String stripped = raw.substring(1, raw.length() - 1);
        // TODO(brandon): Escape things like octal an unicode properly
        return escapeString(stripped);
    }

    @Override
    public String visitIdentifier(final MqlParser.IdentifierContext ctx) {
        return ctx.getText();
    }

    private String escapeString(final String in) {
        final StringBuilder b = new StringBuilder();
        boolean sawEscape = false;
        for (int i = 0; i < in.length(); i++) {
            final Character c = in.charAt(i);
            if (!sawEscape) {
                if (c == '\\') {
                    sawEscape = true;
                } else {
                    b.append(c);
                }
            } else {
                sawEscape = false;
                switch (c) {
                    case '\\':
                        b.append(c);
                        break;
                    case 'b':
                        b.append('\b');
                        break;
                    case 'n':
                        b.append('\n');
                        break;
                    case 't':
                        b.append('\t');
                        break;
                    case 'r':
                        b.append('\r');
                        break;
                    case 'f':
                        b.append('\f');
                        break;
                    case '\'':
                        b.append('\'');
                        break;
                    case '"':
                        b.append('"');
                        break;
                    default:
                        throw new IllegalArgumentException("character '" + c + "' is not a valid escape");
                }
            }
        }
        return b.toString();
    }


    @Override
    public DateTime visitRelativeTime(final MqlParser.RelativeTimeContext ctx) {
        if (ctx.NOW() != null) {
            return DateTime.now();
        } else {
            final double number = Double.parseDouble(ctx.numericLiteral().getText());
            final MqlParser.TimeUnitContext unit = ctx.timeUnit();
            final Duration ago;
            if (unit.SECOND() != null || unit.SECONDS() != null) {
                ago = Duration.millis((long) (number * 1000));
            } else if (unit.MINUTE() != null || unit.MINUTES() != null) {
                ago = Duration.millis((long) (number * 60 * 1000));
            } else if (unit.HOUR() != null || unit.HOURS() != null) {
                ago = Duration.millis((long) (number * 60 * 60 * 1000));
            } else if (unit.DAY() != null || unit.DAYS() != null) {
                ago = Duration.millis((long) (number * 24 * 60 * 60 * 1000));
            } else if (unit.WEEK() != null || unit.WEEKS() != null) {
                ago = Duration.millis((long) (number * 7 * 24 * 60 * 60 * 1000));
            } else if (unit.MONTH() != null || unit.MONTHS() != null) {
                return DateTime.now().withField(DateTimeFieldType.monthOfYear(), DateTime.now().getMonthOfYear() - 1);
            } else {
                throw new RuntimeException("Unknown time unit " + unit.getText());
            }

            return DateTime.now().minus(ago);
        }
    }

    @Override
    public TimeUnit visitTimeUnit(final MqlParser.TimeUnitContext unit) {
        if (unit.SECOND() != null || unit.SECONDS() != null) {
            return TimeUnit.SECONDS;
        } else if (unit.MINUTE() != null || unit.MINUTES() != null) {
            return TimeUnit.MINUTES;
        } else if (unit.HOUR() != null || unit.HOURS() != null) {
            return TimeUnit.HOURS;
        } else if (unit.DAY() != null || unit.DAYS() != null) {
            return TimeUnit.DAYS;
        } else {
            throw new RuntimeException("Unsupported time unit " + unit.getText());
        }
    }

    private StageExecution _previousStage = null;
    private TimeRange _timeRange = null;

    private final Map<String, StageExecution> _stages = Maps.newHashMap();
    private final KairosDbClient _kairosDbClient;
    private final ObjectMapper _mapper;

    private static final Set<String> LIFTABLE_AGGREGATIONS = Sets.newHashSet(
            "min", "max", "merge", "percentile", "count", "avg", "sum", "diff");
    private static final Map<String, ClassBoundBuilderProvider<?>> AGG_BUILDERS = Maps.newHashMap();

    private static <B extends BaseExecution.Builder<B, ?>> ClassBoundBuilderProvider<B> createBuilderProvider(final Class<B> clazz) {
        return (args, mapper) -> mapper.convertValue(args, clazz);
    }

    static {
        AGG_BUILDERS.put("threshold", createBuilderProvider(SimpleThresholdAlertExecution.Builder.class));
        AGG_BUILDERS.put("dataabsent", createBuilderProvider(DataAbsentAlertExecution.Builder.class));
        AGG_BUILDERS.put("union", createBuilderProvider(UnionAggregator.Builder.class));
        AGG_BUILDERS.put("diff", createBuilderProvider(DiffAggregator.Builder.class));
        AGG_BUILDERS.put("top", createBuilderProvider(TopSeriesFilter.Builder.class));
        AGG_BUILDERS.put("bottom", createBuilderProvider(TopSeriesFilter.Builder.class).thenApply(builder -> builder.setInvert(true)));
    }

    @FunctionalInterface
    private interface ClassBoundBuilderProvider<B extends BaseExecution.Builder<B, ?>>  {
        B get(Map<String, Object> args, ObjectMapper mapper);

        default ClassBoundBuilderProvider<B> thenApply(final UnaryOperator<B> operator) {
            return (args, mapper) -> operator.apply(get(args, mapper));
        }
    }

    private static final class TimeRange {
        private TimeRange(final DateTime start, final DateTime end) {
            _start = start;
            _end = end;
        }

        private final DateTime _start;
        private final DateTime _end;
    }
}
