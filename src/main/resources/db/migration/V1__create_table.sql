CREATE TABLE reisetilskudd (
    reisetilskudd_id varchar(36) primary key,
    sykmelding_id varchar(36) not null,
    fnr varchar(11) not null,
    aktor_id varchar(13) not null,
    fom date not null,
    tom date not null,
    arbeidsgiver_orgnummer varchar(9),
    arbeidsgiver_navn text
);

CREATE TABLE kvitteringer (
    kvittering_id varchar(36) primary key,
    reisetilskudd_id varchar (36) references reisetilskudd(reisetilskudd_id) not null,
    belop int not null,
    dato date not null
);

