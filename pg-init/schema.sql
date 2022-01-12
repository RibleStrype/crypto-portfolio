CREATE TABLE assets(
    symbol VARCHAR PRIMARY KEY,
    name VARCHAR NOT NULL,
    price_usd NUMERIC NOT NULL
);

CREATE TABLE investors(
    id BIGINT PRIMARY KEY,
    name VARCHAR NOT NULL
);

CREATE TABLE investments(
    investor_id BIGINT NOT NULL,
    asset VARCHAR NOT NULL,
    amount NUMERIC NOT NULL,
    PRIMARY KEY (investor_id, asset),
    CONSTRAINT fk_investors
        FOREIGN KEY(investor_id) REFERENCES investors(id)
);

INSERT INTO investors(id, name) VALUES(1, 'Default');
