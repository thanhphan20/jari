SELECT 'CREATE DATABASE jari_user' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'jari_user')\gexec
SELECT 'CREATE DATABASE jari_project' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'jari_project')\gexec
SELECT 'CREATE DATABASE jari_task' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'jari_task')\gexec
SELECT 'CREATE DATABASE jari_notification' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'jari_notification')\gexec
