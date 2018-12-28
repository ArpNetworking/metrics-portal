set -e
/sbin/chkconfig --add metrics-portal
mkdir -p /opt/metrics-portal/logs
chown metrics-portal:metrics-portal /opt/metrics-portal/logs
mkdir -p /opt/metrics-portal/config/pipelines
mkdir -p /var/run/metrics-portal
chown metrics-portal:metrics-portal /var/run/metrics-portal
