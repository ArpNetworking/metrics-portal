set -e
getent group metrics-portal >/dev/null || groupadd -r metrics-portal
getent passwd metrics-portal >/dev/null || \
    useradd -r -g metrics-portal -d /opt/metrics-portal -s /sbin/nologin \
    -c "Account used for isolation of metrics portal" metrics-portal
