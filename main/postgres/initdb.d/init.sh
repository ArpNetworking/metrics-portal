#!/usr/bin/env bash

psql_root_postgres=( psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --no-password --dbname "$POSTGRES_DB" )
psql_root_metrics=( psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --no-password --dbname "metrics" )
psql_metrics_dba=( psql -v ON_ERROR_STOP=1 --username "metrics_dba" --no-password --dbname "metrics" )
psql_pekko_dba=( psql -v ON_ERROR_STOP=1 --username "pekko_dba" --no-password --dbname "metrics" )

# Run as database administrator against the postgres database
# NOTE: The password is already set by the caller
# Ref: https://github.com/docker-library/postgres/blob/master/10/docker-entrypoint.sh#L147
echo "$0: running /docker-entrypoint-initdb.d/init.sql.1"; "${psql_root_postgres[@]}" -f "/docker-entrypoint-initdb.d/init.sql.1";

# Run as database administrator against the metrics database
# NOTE: The password is already set by the caller
# Ref: https://github.com/docker-library/postgres/blob/master/10/docker-entrypoint.sh#L147
echo "$0: running /docker-entrypoint-initdb.d/init.sql.1"; "${psql_root_metrics[@]}" -f "/docker-entrypoint-initdb.d/init.sql.2";

# Run as metrics dba
export PGPASSWORD="metrics_dba_password"
echo "$0: running /docker-entrypoint-initdb.d/init.sql.2"; "${psql_metrics_dba[@]}" -f "/docker-entrypoint-initdb.d/init.sql.3";

# Run as pekko dba
export PGPASSWORD="pekko_dba_password"
echo "$0: running /docker-entrypoint-initdb.d/init.sql.3"; "${psql_pekko_dba[@]}" -f "/docker-entrypoint-initdb.d/init.sql.4";

# Restore the root password
export PGPASSWORD="${PGPASSWORD:-$POSTGRES_PASSWORD}"
