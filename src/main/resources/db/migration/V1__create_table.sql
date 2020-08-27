CREATE TABLE REISETILSKUDD
(
    REISETILSKUDD_ID            VARCHAR(36) PRIMARY KEY,
    SYKMELDING_ID               VARCHAR(36)                  NOT NULL,
    FNR                         VARCHAR(11)                  NOT NULL,
    OPPRETTET                   TIMESTAMP                    NOT NULL,
    ENDRET                      TIMESTAMP                    NOT NULL,
    FOM                         DATE                         NOT NULL,
    TOM                         DATE                         NOT NULL,
    ARBEIDSGIVER_ORGNUMMER      VARCHAR(9),
    ARBEIDSGIVER_NAVN           TEXT,
    UTBETALING_TIL_ARBEIDSGIVER INTEGER          DEFAULT 0   NOT NULL,
    GAR                         INTEGER          DEFAULT 0   NOT NULL,
    SYKLER                      INTEGER          DEFAULT 0   NOT NULL,
    EGEN_BIL                    DOUBLE PRECISION DEFAULT 0.0 NOT NULL,
    KOLLEKTIVTRANSPORT          DOUBLE PRECISION DEFAULT 0.0 NOT NULL
);


CREATE INDEX REISETILSKUDD_FNR_IDX ON REISETILSKUDD (FNR);

CREATE TABLE KVITTERINGER
(
    KVITTERING_ID    VARCHAR(36) PRIMARY KEY,
    REISETILSKUDD_ID VARCHAR(36) REFERENCES REISETILSKUDD (REISETILSKUDD_ID) NOT NULL,
    NAVN             VARCHAR(256)                                            NOT NULL,
    BELOP            DOUBLE PRECISION                                        NOT NULL,
    FOM              DATE                                                    NOT NULL,
    TOM              DATE,
    STORRELSE        BIGINT                                                  NOT NULL,
    TRANSPORTMIDDEL  TEXT                                                    NOT NULL,
    OPPRETTET        TIMESTAMP                                               NOT NULL
);


