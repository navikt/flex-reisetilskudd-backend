DROP TABLE KVITTERINGER;
DROP TABLE REISETILSKUDD;

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE REISETILSKUDD_SOKNAD
(
    ID                     VARCHAR(36) DEFAULT uuid_generate_v4() PRIMARY KEY NOT NULL,
    SYKMELDING_ID          VARCHAR(36)                                        NOT NULL,
    FNR                    VARCHAR(11)                                        NOT NULL,
    OPPRETTET              TIMESTAMP WITH TIME ZONE                           NOT NULL,
    ENDRET                 TIMESTAMP WITH TIME ZONE                           NOT NULL,
    AVBRUTT                TIMESTAMP WITH TIME ZONE                           NULL,
    SENDT                  TIMESTAMP WITH TIME ZONE                           NULL,
    FOM                    DATE                                               NOT NULL,
    TOM                    DATE                                               NOT NULL,
    ARBEIDSGIVER_ORGNUMMER VARCHAR(9)                                         NULL,
    ARBEIDSGIVER_NAVN      VARCHAR(255)                                       NULL,
    STATUS                 VARCHAR(10)                                        NOT NULL
);


CREATE INDEX REISETILSKUDD_FNR_IDX ON REISETILSKUDD (FNR);

CREATE TABLE KVITTERING
(
    ID               VARCHAR(36) DEFAULT uuid_generate_v4() PRIMARY KEY NOT NULL,
    REISETILSKUDD_ID VARCHAR(36) REFERENCES REISETILSKUDD_SOKNAD (ID)   NOT NULL,
    DATO_FOR_UTGIFT  DATE                                               NOT NULL,
    BELOP            INTEGER                                            NOT NULL,
    BLOB_ID          VARCHAR(36)                                        NOT NULL,
    TYPE_UTGIFT      VARCHAR(36)                                        NOT NULL,
    OPPRETTET        TIMESTAMP WITH TIME ZONE                           NOT NULL
);


