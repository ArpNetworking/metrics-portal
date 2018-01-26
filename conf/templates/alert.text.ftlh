Alert "${alert.name}" has gone into alarm at ${trigger.time.toString("HH:mm MM/dd/YYYY z")}.
See ${alertUrl}

${alert.comment}

<#list trigger.args as key, value>
${key} = ${value}
</#list>
