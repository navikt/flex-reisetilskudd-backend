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
    override val kafkaBootstrapServers: String = getEnvVar("KAFKA_BOOTSTRAP_SERVERS_URL")
) : KafkaConfig

data class VaultSecrets (
    override val kafkaUsername: String = getFileAsString("/secrets/serviceuser/username").trim(),
    override val kafkaPassword: String = getFileAsString("/secrets/serviceuser/password").trim()
) : KafkaCredentials

fun getEnvVar(varName: String, defaultValue: String? = null) =
    System.getenv(varName) ?: defaultValue ?: throw RuntimeException("Missing required variable \"$varName\"")

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)