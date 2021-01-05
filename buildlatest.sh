#!/bin/bash
echo "Bygger flex-reisetilskudd-backend for docker compose utvikling"
rm -rf ./build/libs
./gradlew shadowJar -x test
docker build -t flex-reisetilskudd-backend:latest .
