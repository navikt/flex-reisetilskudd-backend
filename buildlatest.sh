#!/bin/bash
echo "Bygger flex-reisetilskudd-backend for docker compose utvikling"
rm -rf ./build
./gradlew bootJar
docker build -t flex-reisetilskudd-backend:latest .
