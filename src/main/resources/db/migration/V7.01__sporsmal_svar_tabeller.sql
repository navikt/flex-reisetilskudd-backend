ALTER TABLE REISETILSKUDD_SOKNAD
    ALTER COLUMN ID DROP DEFAULT;

ALTER TABLE KVITTERING
    ALTER COLUMN ID DROP DEFAULT;


CREATE TABLE SPORSMAL
(
    ID                                    VARCHAR(36) PRIMARY KEY                          NOT NULL,
    REISETILSKUDD_SOKNAD_ID               VARCHAR(36) REFERENCES REISETILSKUDD_SOKNAD (ID) NULL,
    OVERSPORSMAL_ID                       VARCHAR(36) REFERENCES SPORSMAL (ID)             NULL,
    OVERSKRIFT                            VARCHAR(128)                                     NULL,
    SPORSMALSTEKST                        VARCHAR(512)                                     NULL,
    UNDERTEKST                            VARCHAR(512)                                     NULL,
    TEKST                                 VARCHAR(64)                                      NULL,
    TAG                                   VARCHAR(64)                                      NOT NULL,
    SVARTYPE                              VARCHAR(64)                                      NOT NULL,
    MIN                                   VARCHAR(128)                                     NULL,
    MAX                                   VARCHAR(128)                                     NULL,
    KRITERIE_FOR_VISNING_AV_UNDERSPORSMAL VARCHAR(128)                                     NULL
);


CREATE TABLE SVAR
(
    ID          VARCHAR(36) PRIMARY KEY              NOT NULL,
    SPORSMAL_ID VARCHAR(36) REFERENCES SPORSMAL (ID) NOT NULL,
    VERDI       VARCHAR(128)                         NOT NULL
);

