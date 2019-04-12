set -e
if [ "$1" = 0 ]; then
  /sbin/service metrics-portal stop > /dev/null 2>&1
  /sbin/chkconfig --del metrics-portal
fi
exit 0
