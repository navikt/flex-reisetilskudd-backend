package no.nav.helse.flex.kafka
/*
@KtorExperimentalAPI
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
internal class SykmeldingKafkaServiceTest {
    companion object {
        lateinit var testApp: TestApp
        val reisetilskuddPeriode = SykmeldingsperiodeDTO(
            type = PeriodetypeDTO.REISETILSKUDD,
            reisetilskudd = true,
            fom = LocalDate.now().minusDays(10),
            tom = LocalDate.now(),
            gradert = null,
            behandlingsdager = null,
            innspillTilArbeidsgiver = null,
            aktivitetIkkeMulig = null
        )
        val tomPerioder = lagSykmeldingMessage(
            sykmeldingsperioder = emptyList()
        )
        val utenReisetilskudd = lagSykmeldingMessage(
            sykmeldingsperioder = listOf(
                reisetilskuddPeriode.copy(
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    reisetilskudd = false
                )
            )
        )
        val ikkeAllePerioderErReisetilskudd = lagSykmeldingMessage(
            sykmeldingsperioder = listOf(
                reisetilskuddPeriode,
                reisetilskuddPeriode.copy(
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    reisetilskudd = false
                )
            )
        )
        val gradertPerioe = lagSykmeldingMessage(
            sykmeldingsperioder = listOf(
                reisetilskuddPeriode.copy(
                    gradert = GradertDTO(
                        grad = 50,
                        reisetilskudd = true
                    )
                )
            )
        )
        val feilVedScanning = lagSykmeldingMessage(
            sykmeldingsperioder = listOf(
                reisetilskuddPeriode.copy(
                    type = PeriodetypeDTO.AKTIVITET_IKKE_MULIG,
                    reisetilskudd = true
                )
            )
        )
        val enGyldigPeriode = lagSykmeldingMessage(
            sykmeldingsperioder = listOf(
                reisetilskuddPeriode
            )
        )
        val flereGyldigePerioder = lagSykmeldingMessage(
            sykmeldingsperioder = listOf(
                reisetilskuddPeriode,
                reisetilskuddPeriode.copy(
                    fom = LocalDate.now().plusDays(1),
                    tom = LocalDate.now().plusDays(10)
                )
            )
        )

        val sykmeldinger = listOf(
            tomPerioder,
            utenReisetilskudd,
            ikkeAllePerioderErReisetilskudd,
            gradertPerioe,
            feilVedScanning,
            enGyldigPeriode,
            flereGyldigePerioder
        )

        @BeforeAll
        @JvmStatic
        internal fun beforeAll() {
            testApp = skapTestApplication()
        }
    }

    @Test
    @Order(0)
    fun `Det er ingen reisetilskudd til å begynne med`() {
        with(testApp) {
            with(
                engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                    medSelvbetjeningToken("fnr")
                }
            ) {
                response.status() shouldBeEqualTo HttpStatusCode.OK
                response.content!!.tilReisetilskuddListe().size `should be equal to` 0
            }
        }
    }

    @Test
    @Order(1)
    fun `Alle sykmeldinger publiseres og konsumeres`() {
        with(testApp) {
            applicationState.alive = true
            applicationState.ready = true
            sykmeldinger.forEach { syk ->
                sykmeldingKafkaProducer.send(
                    ProducerRecord(
                        "syfo-sendt-sykmelding",
                        syk
                    )
                ).get()
            }

            runBlocking {
                stopApplicationNårAntallKafkaMeldingerErLest(
                    sykmeldingKafkaConsumer,
                    applicationState,
                    antallKafkaMeldinger = sykmeldinger.size
                )
                sykmeldingKafkaService.start()
            }
        }
    }

    @Test
    @Order(2)
    fun `Reisetilskuddene er tilgjengelig`() {
        with(testApp) {
            with(
                engine.handleRequest(HttpMethod.Get, "/api/v1/reisetilskudd") {
                    medSelvbetjeningToken("fnr")
                }
            ) {
                response.status() shouldBeEqualTo HttpStatusCode.OK
                val reisetilskuddene = response.content!!.tilReisetilskuddListe()
                reisetilskuddene.size `should be equal to` 3
                reisetilskuddene.filter {
                    it.sykmeldingId == enGyldigPeriode.sykmelding.id
                }.size `should be equal to` 1
                reisetilskuddene.filter {
                    it.sykmeldingId == flereGyldigePerioder.sykmelding.id
                }.size `should be equal to` 2
            }
        }
    }
}
*/ // TODO
