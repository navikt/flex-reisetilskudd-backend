#!/bin/bash
echo "Bygger flex-reisetilskudd-backend for docker compose utvikling"
./gradlew ktlintFormat
./gradlew shadowJar
docker build -t flex-reisetilskudd-backend:latest .
