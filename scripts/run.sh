#!/bin/sh
set -eu
exec java -jar /var/task/app.jar --spring.flyway.enabled=false --spring.jpa.hibernate.ddl-auto=validate
