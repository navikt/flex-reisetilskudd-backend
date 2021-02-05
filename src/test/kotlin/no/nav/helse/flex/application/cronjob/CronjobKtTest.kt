package no.nav.helse.flex.application.cronjob

/*
@KtorExperimentalAPI
internal class CronjobKtTest {
    companion object {
        val applicationState = ApplicationState()
        val db = TestDB()
        val env = mockk<Environment>()
        val kafka = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"))
            .withNetwork(Network.newNetwork())
        val aivenKafkaConfig = mockk<AivenKafkaConfig>()
        val podLeaderCoordinator = mockk<PodLeaderCoordinator>()
        val cronjob = Cronjob(
            applicationState = applicationState,
            env = env,
            database = db,
            aivenKafkaConfig = aivenKafkaConfig,
            podLeaderCoordinator = podLeaderCoordinator
        )

        @BeforeAll
        @JvmStatic
        internal fun beforeAll() {
            kafka.start()
        }
    }

    @BeforeEach
    fun beforeEach() {
        val producerProperties = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to JacksonKafkaSerializer::class.java
        )

        clearAllMocks()
        every { env.cluster } returns "test"
        every { env.electorPath } returns "dont_look_for_leader"
        every { aivenKafkaConfig.producer() } returns KafkaProducer(producerProperties)
        every { podLeaderCoordinator.isLeader() } returns true

        applicationState.alive = true
        applicationState.ready = true
    }

    @Test
    fun `aktivering av reisetilskudd`() {
        val fnr = "123aktiver"
        val now = LocalDate.now()
        val nr1 = reisetilskudd(
            fnr = fnr,
            fom = now.minusDays(20),
            tom = now.minusDays(11),
            status = ReisetilskuddStatus.SENDT
        )
        val nr2 = reisetilskudd(
            fnr = fnr,
            fom = now.minusDays(10),
            tom = now.minusDays(1),
            status = ReisetilskuddStatus.ÅPEN
        )
        val nr3 = reisetilskudd(
            fnr = fnr,
            fom = now,
            tom = now.plusDays(9),
            status = ReisetilskuddStatus.FREMTIDIG
        )
        val nr4 = reisetilskudd(
            fnr = fnr,
            fom = now.plusDays(10),
            tom = now.plusDays(19),
            status = ReisetilskuddStatus.FREMTIDIG
        )
        db.lagreReisetilskudd(nr4)
        db.lagreReisetilskudd(nr3)
        db.lagreReisetilskudd(nr2)
        db.lagreReisetilskudd(nr1)

        val reisetilskuddeneFør = db.hentReisetilskuddene(fnr)
        reisetilskuddeneFør.size shouldBe 4
        reisetilskuddeneFør[0].status shouldBeEqualTo ReisetilskuddStatus.SENDT
        reisetilskuddeneFør[1].status shouldBeEqualTo ReisetilskuddStatus.ÅPEN
        reisetilskuddeneFør[2].status shouldBeEqualTo ReisetilskuddStatus.FREMTIDIG
        reisetilskuddeneFør[3].status shouldBeEqualTo ReisetilskuddStatus.FREMTIDIG

        cronjob.run()

        val reisetilskuddeneEtter = db.hentReisetilskuddene(fnr)
        reisetilskuddeneEtter.size shouldBe 4
        reisetilskuddeneEtter[0].status shouldBeEqualTo ReisetilskuddStatus.SENDT
        reisetilskuddeneEtter[1].status shouldBeEqualTo ReisetilskuddStatus.SENDBAR
        reisetilskuddeneEtter[2].status shouldBeEqualTo ReisetilskuddStatus.ÅPEN
        reisetilskuddeneEtter[3].status shouldBeEqualTo ReisetilskuddStatus.FREMTIDIG
    }

    private fun reisetilskudd(
        reisetilskuddId: String = UUID.randomUUID().toString(),
        sykmeldingId: String = UUID.randomUUID().toString(),
        fnr: String,
        fom: LocalDate = LocalDate.now().minusDays(10),
        tom: LocalDate = LocalDate.now(),
        orgNummer: String = "12345",
        orgNavn: String = "min arbeidsplass",
        status: ReisetilskuddStatus? = null,
        oppfølgende: Boolean = false
    ) = Reisetilskudd(
        reisetilskuddId = reisetilskuddId,
        sykmeldingId = sykmeldingId,
        fnr = fnr,
        fom = fom,
        tom = tom,
        orgNummer = orgNummer,
        orgNavn = orgNavn,
        status = status ?: reisetilskuddStatus(fom, tom),
        oppfølgende = oppfølgende,
        opprettet = Instant.now(),
    )
}
*/ // TODO
