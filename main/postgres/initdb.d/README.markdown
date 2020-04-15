This directory helps you prepare your Postgres instance for MPortal to run.

Context: when MPortal runs for the first time, it will create all the Postgres tables it needs;
but to do that, it assumes that certain roles/"schemas" are already set up in Postgres.

Here are instructions for how to take a fresh Postgres instance and make it ready for MPortal to run for the first time:

- First, create two users (probably by logging in as the root user via PGAdmin), named `metrics_dba` and `akka_dba`.
- `export POSTGRES_USER=postgres` (or whatever your root user's name is)
- `export POSTGRES_DB=????` (TODO(spencerpearson): what is this? Our production Postgres seems to have databases named `postgres` and `rdsadmin`, plus `metrics` which these scripts seem to create.)
- `export POSTGRES_PASSWORD=<your secret postgres-root-user password>`
- locally `chmod 700 ./init.sh` and edit the `export PGPASSWORD="..."` lines to contain the actual passwords for the metrics_dba and akka_dba users
- `./init.sh`
- `git checkout HEAD -- ./init.sh` to avoid having those secrets lying around
