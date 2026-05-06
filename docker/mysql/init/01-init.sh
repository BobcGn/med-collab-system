#!/bin/bash
set -e

mysql -u root -p"${MYSQL_ROOT_PASSWORD}" <<-EOSQL
  CREATE DATABASE IF NOT EXISTS med_auth
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

  CREATE DATABASE IF NOT EXISTS patient
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

  CREATE DATABASE IF NOT EXISTS analysis
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

  CREATE USER IF NOT EXISTS '${DB_USER:-Hyy}'@'%'
    IDENTIFIED BY '${DB_PASSWORD:-hyy20060420}';

  GRANT ALL PRIVILEGES ON med_auth.* TO '${DB_USER:-Hyy}'@'%';
  GRANT ALL PRIVILEGES ON patient.* TO '${DB_USER:-Hyy}'@'%';
  GRANT ALL PRIVILEGES ON analysis.* TO '${DB_USER:-Hyy}'@'%';

  FLUSH PRIVILEGES;
EOSQL
