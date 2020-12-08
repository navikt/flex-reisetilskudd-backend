# flex-reisetilskudd-backend
This project contains the application code and infrastructure for flex-reisetilskudd-backend
## Technologies used
* Kotlin
* Ktor
* Gradle

## Getting started
### Getting github-package-registry packages NAV-IT
Some packages used in this repo is uploaded to the Github Package Registry which requires authentication. It can, for example, be solved like this in Gradle:
```kotlin
val githubUser: String by project
val githubPassword: String by project
repositories {
    maven {
        credentials {
            username = githubUser
            password = githubPassword
        }
        setUrl("https://maven.pkg.github.com/navikt/syfosm-common")
    }
}
```

`githubUser` and `githubPassword` can be put into a separate file `~/.gradle/gradle.properties` with the following content:

```sh
githubUser=x-access-token
githubPassword=[token]
```

Replace `[token]` with a personal access token with scope `read:packages`.

Alternatively, the variables can be configured via environment variables:

* `ORG_GRADLE_PROJECT_githubUser`
* `ORG_GRADLE_PROJECT_githubPassword`

or the command line:

```sh
./gradlew -PgithubUser=x-access-token -PgithubPassword=[token]
```

### Building the application
#### Compile and package application
To build locally and run the integration tests you can simply run `./gradlew shadowJar` or  on windows
`gradlew.bat shadowJar`

#### Creating a docker image
Creating a docker image should be as simple as `docker build -t "no.nav.syfo" .`

#### Running a docker image
`docker run --rm -it -p 8080:8080 "no.nav.syfo"`

#### Creating a docker image for use in docker-compose
`./buildlatest.sh`

#### Tldr

## Kafka topic
#### Applying changes
```
kubectl config use-context dev-gcp
kubectl apply -f topics/dev/aapen-reisetilskudd.yaml -n flex
kubectl config use-context prod-gcp
kubectl apply -f topics/prod/aapen-reisetilskudd.yaml -n flex
```
#### Verify topic changes
```
kubectl get topics -n flex
kubectl describe topic aapen-reisetilskudd -n flex
```

## Contact us
flex@nav.no

### For NAV employees
We are available at the Slack channel #flex
