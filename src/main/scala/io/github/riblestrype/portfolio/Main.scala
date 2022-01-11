package io.github.riblestrype.portfolio

import cats.effect.kernel.Resource
import cats.effect.{IO, IOApp}
import io.github.riblestrype.portfolio.CoinMarketCapClient.ApiKey
import natchez.Trace.Implicits.noop
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import org.typelevel.log4cats.Logger
import org.typelevel.log4cats.slf4j.Slf4jLogger
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import pureconfig.module.catseffect.syntax.CatsEffectConfigSource
import skunk.Session

object Main extends IOApp.Simple {
  implicit val logger: Logger[IO] = Slf4jLogger.getLogger[IO]

  override def run: IO[Unit] =
    services.use { srv =>
      IO.println(s"HTTP server listening on ${srv.server.address}") *>
        AssetSync.start(srv.repositories.assets, srv.coinMarketCapClient)
    }

  def coinMarketCapClient: Resource[IO, CoinMarketCapClient[IO]] =
    for {
      config <- Resource.eval(
        ConfigSource.default.at("coin-market-cap").loadF[IO, CoinMarketCapConfig]()
      )
      client <- CoinMarketCapClient.of[IO](ApiKey(config.apiKey))
    } yield client

  def repositories: Resource[IO, Repositories] = {
    for {
      config <- Resource.eval(ConfigSource.default.at("db").loadF[IO, DbConfig]())
      sessions <- Session.pooled[IO](
        host = config.host,
        user = config.username,
        password = config.password,
        database = config.database,
        max = 10
      )
    } yield Repositories(
      assets = Assets.of(sessions),
      investments = Investments.of(sessions)
    )

  }

  def httpServer(investments: Investments[IO], portfolios: Portfolios[IO]): Resource[IO, Server] =
    EmberServerBuilder.default[IO].withHttpApp(Http.app(investments, portfolios)).build

  def services: Resource[IO, Services] =
    for {
      repositories        <- repositories
      coinMarketCapClient <- coinMarketCapClient
      portfolios = Portfolios.of(repositories.assets, repositories.investments)
      server <- httpServer(repositories.investments, portfolios)
    } yield Services(repositories, coinMarketCapClient, server)

  final case class Repositories(
      assets: Assets[IO],
      investments: Investments[IO]
  )

  final case class Services(
      repositories: Repositories,
      coinMarketCapClient: CoinMarketCapClient[IO],
      server: Server
  )

  final case class DbConfig(
      username: String,
      password: Option[String],
      host: String,
      database: String
  )

  final case class CoinMarketCapConfig(
      apiKey: String
  )
}
