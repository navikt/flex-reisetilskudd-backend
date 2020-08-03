CREATE TABLE reisetilskudd (
    reisetilskudd_id varchar(36) primary key,
    sykmelding_id varchar(36) not null,
    fnr varchar(11) not null,
    aktor_id varchar(13) not null,
    fom date not null,
    tom date not null,
    arbeidsgiver_orgnummer varchar(9),
    arbeidsgiver_navn text,
    utbetaling_til_arbeidsgiver integer default 0 not null,
    gar integer default 0 not null,
    sykler integer default 0 not null,
    egen_bil double precision default 0.0 not null,
    kollektivtransport double precision default 0.0 not null
);

CREATE TABLE kvitteringer (
    kvittering_id varchar(36) primary key,
    reisetilskudd_id varchar (36) references reisetilskudd(reisetilskudd_id) not null,
    belop double precision not null,
    fom date not null,
    tom date,
    storrelse bigint not null,
    transportmiddel text not null
);


