# Crypto Portfolio

A service that calculates value of a user's crypto portfolio. 
A user specifies how much of which crypto assets they have and the service calculates the value of the portfolio.
The current cryptocurrency prices are being periodically fetched from [CoinMarketCap](http://coinmarketcap.com).

## Usage

The service uses [PostgreSQL](https://www.postgresql.org/) for data storage. 
The simplest way to spin up a running instance is to use Docker Compose. \
Execution the following command from the project folder: 

```docker-compose up -d```

It will spin up a new instance and create necessary tables. It will also create a default investor with id of `1`.

To run the service execute:

```sbt run -DCOIN_MARKET_CAP_API_KEY=<your CoinMarketCap API key>```

## API

To add an asset to portfolio send a `PUT` request to `/investments/<investor id>` endpoint, example:
```
curl -X PUT -d '{"asset": "ETH", "amount": 1.2}' http://localhost:8080/investments/1
```

To get the value of a portfolio send a `GET` request to `/portfolios/<investor id>` endpoint, example:
```
curl http://localhost:8080/portfolios/1
```

