package no.nav.helse.flex

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val dbHost: String = getEnvVar("NAIS_DATABASE_FLEX_REISETILSKUDD_BACKEND_FLEX_REISETILSKUDD_DB_HOST"),
    val dbPort: String = getEnvVar("NAIS_DATABASE_FLEX_REISETILSKUDD_BACKEND_FLEX_REISETILSKUDD_DB_PORT"),
    val dbName: String = getEnvVar("NAIS_DATABASE_FLEX_REISETILSKUDD_BACKEND_FLEX_REISETILSKUDD_DB_DATABASE"),
    val dbUsername: String = getEnvVar("NAIS_DATABASE_FLEX_REISETILSKUDD_BACKEND_FLEX_REISETILSKUDD_DB_USERNAME"),
    val dbPwd: String = getEnvVar("NAIS_DATABASE_FLEX_REISETILSKUDD_BACKEND_FLEX_REISETILSKUDD_DB_PASSWORD"),
    val serviceuserUsername: String = getEnvVar("SERVICEUSER_USERNAME"),
    val serviceuserPassword: String = getEnvVar("SERVICEUSER_PASSWORD"),
    val loginserviceIdportenDiscoveryUrl: String = getEnvVar("LOGINSERVICE_IDPORTEN_DISCOVERY_URL"),
    val electorPath: String = getEnvVar("ELECTOR_PATH"),
    val sidecarInitialDelay: Long = getEnvVar("SIDECAR_INITIAL_DELAY", "15000").toLong(),
    val loginserviceIdportenAudience: String = getEnvVar("LOGINSERVICE_IDPORTEN_AUDIENCE")
) : KafkaConfig {

    fun hentKafkaCredentials(): KafkaCredentials {
        return object : KafkaCredentials {
            override val kafkaPassword: String
                get() = serviceuserPassword
            override val kafkaUsername: String
                get() = serviceuserUsername
        }
    }

    fun jdbcUrl(): String {
        return "jdbc:postgresql://$dbHost:$dbPort/$dbName"
    }

    // Aiven kafka - hentes på nytt hver gang
    fun bootstrapServers() = getEnvVar("KAFKA_BROKERS")
    fun sslKeystoreLocation() = getEnvVar("KAFKA_KEYSTORE_PATH")
    fun sslKeystorePassword() = getEnvVar("KAFKA_CREDSTORE_PASSWORD")
    fun sslTruststoreLocation() = getEnvVar("KAFKA_TRUSTSTORE_PATH")
    fun sslTruststorePassword() = getEnvVar("KAFKA_CREDSTORE_PASSWORD")
    fun securityProtocol() = getEnvVar("KAFKA_SECURITY_PROTOCOL", "SSL")
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
