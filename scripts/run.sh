#!/bin/sh
set -eu

# Keep migrations disabled until the initial schema has been reviewed.
exec java -jar /var/task/app.jar --spring.flyway.enabled=false --spring.jpa.hibernate.ddl-auto=validate
