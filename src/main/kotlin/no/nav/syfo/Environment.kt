package no.nav.syfo

import no.nav.syfo.kafka.KafkaConfig
import no.nav.syfo.kafka.KafkaCredentials
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

data class Environment(
    val applicationPort: Int = getEnvVar("APPLICATION_PORT", "8080").toInt(),
    val applicationName: String = getEnvVar("NAIS_APP_NAME", "flex-reisetilskudd-backend"),
    val sendtSykmeldingTopic: String = getEnvVar("SENDT_SYKMELDING_TOPIC", "syfo-sendt-sykmelding"),
    val bekreftetSykmeldingTopic: String = getEnvVar("BEKREFTET_SYKMELDING_TOPIC", "syfo-bekreftet-sykmelding"),
    val mountPathVault: String = getEnvVar("MOUNT_PATH_VAULT"),
    val databaseName: String = getEnvVar("DATABASE_NAME", "reisetilskudd"),
    val flexreisetilskuddDBURL: String = getEnvVar("REISETILSKUDD_DB_URL"),
    val jwtIssuer: String = getEnvVar("JWT_ISSUER"),
    val cluster: String = getEnvVar("NAIS_CLUSTER_NAME"),
    val clientId: String = getEnvVar("CLIENT_ID"),
    val appIds: List<String> = getEnvVar("ALLOWED_APP_IDS")
        .split(",")
        .map { it.trim() },
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL")

) : KafkaConfig

data class VaultSecrets(
    val serviceuserUsername: String,
    val serviceuserPassword: String,
    val syfomockUsername: String,
    val syfomockPassword: String,
    val oidcWellKnownUri: String,
    val loginserviceClientId: String,
    val internalJwtIssuer: String,
    val internalJwtWellKnownUri: String,
    val internalLoginServiceClientId: String
) : KafkaCredentials {
    override val kafkaUsername: String = serviceuserUsername
    override val kafkaPassword: String = serviceuserPassword
}

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8).trim()
