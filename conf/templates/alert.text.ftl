<#ftl output_format="plainText">
Alert "${alert.name}" on ${groupBy} has gone into alarm at ${trigger.time.toString("HH:mm MM/dd/YYYY z")}.
${trigger.message}
See ${alertUrl}
${alert.comment}

<#list trigger.args as key, value>
${key} = ${value}
</#list>
