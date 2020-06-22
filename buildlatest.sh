echo "Bygger flex-reisetilskudd-backend for docker compose utvikling"
./gradlew shadowJar
docker build -t flex-reisetilskudd-backend:latest .
