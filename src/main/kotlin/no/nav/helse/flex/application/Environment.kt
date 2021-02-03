package no.nav.helse.flex.application

class Environmddent(
    val applicationName: String = getEnvVar("NAIS_APP_NAME"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val serviceuserUsername: String = getEnvVar("SERVICEUSER_USERNAME"),
    val serviceuserPassword: String = getEnvVar("SERVICEUSER_PASSWORD"),
    val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL"),
    val kafkaAutoOffsetReset: String = getEnvVar("KAFKA_AUTO_OFFSET_RESET", "none"),
    val kafkaSecurityProtocol: String = getEnvVar("KAFKA_SECURITY_PROTOCOL", "SASL_SSL")
)

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")
