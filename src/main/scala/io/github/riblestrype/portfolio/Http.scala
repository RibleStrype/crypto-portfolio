package io.github.riblestrype.portfolio

import cats.Monad
import cats.effect.kernel.Async
import cats.syntax.all._
import io.circe.generic.semiauto.deriveEncoder
import io.circe.syntax._
import io.circe.{Encoder, Json}
import io.github.riblestrype.portfolio.Investments.{Investment, InvestorId}
import io.github.riblestrype.portfolio.Portfolios.{Portfolio, ValuedAsset, ValuedAssets}
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.circe.JsonDecoder
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.http4s.server.middleware.{AutoSlash, RequestLogger, ResponseLogger, Timeout}
import org.http4s.{HttpApp, HttpRoutes}

import scala.concurrent.duration._

object Http {

  def app[F[_]: Async](investments: Investments[F], portfolios: Portfolios[F]): HttpApp[F] = {
    val middleware: HttpRoutes[F] => HttpRoutes[F] = { http: HttpRoutes[F] =>
      AutoSlash(http)
    } andThen { http =>
      Timeout(15.seconds)(http)
    }

    val closedMiddleware: HttpApp[F] => HttpApp[F] = {
      { http: HttpApp[F] =>
        RequestLogger.httpApp(logHeaders = true, logBody = true)(http)
      } andThen { http: HttpApp[F] =>
        ResponseLogger.httpApp(logHeaders = true, logBody = true)(http)
      }
    }
    val allRoutes = routes(investments, portfolios)
    closedMiddleware(middleware(allRoutes).orNotFound)
  }

  private def routes[F[_]: Monad: JsonDecoder](
      investments: Investments[F],
      portfolios: Portfolios[F]
  ): HttpRoutes[F] =
    Router(
      "/investments" -> investmentRoutes(investments),
      "/portfolios"  -> portfolioRoutes(portfolios)
    )

  private def investmentRoutes[F[_]: Monad: JsonDecoder](
      investments: Investments[F]
  ): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    HttpRoutes.of[F] {
      case req @ PUT -> Root / InvestorIdVar(investorId) =>
        JsonDecoder[F].asJsonDecode[Investment](req).flatMap { investment =>
          investments.save(investorId, investment) *> Ok(investment)
        }
    }
  }

  private def portfolioRoutes[F[_]: Monad](
      portfolios: Portfolios[F]
  ): HttpRoutes[F] = {
    val dsl = Http4sDsl[F]
    import dsl._

    implicit val portfolioEncoder: Encoder[Portfolio] = {
      implicit val valuedAssetEncoder: Encoder[ValuedAsset] =
        va =>
          Json.obj(
            "symbol"   -> va.symbol.asJson,
            "name"     -> va.name.asJson,
            "usdPrice" -> va.usdPrice.asJson,
            "amount"   -> va.amount.asJson,
            "subTotal" -> va.subTotal.asJson
          )
      implicit val valuedAssetsEncoder: Encoder[ValuedAssets] =
        va =>
          Json.obj(
            "assets" -> va.assets.asJson,
            "total"  -> va.total.asJson
          )
      deriveEncoder[Portfolio]
    }

    HttpRoutes.of[F] {
      case GET -> Root / InvestorIdVar(investorId) =>
        Ok(portfolios.of(investorId))
    }
  }

  object InvestorIdVar {
    def unapply(str: String): Option[InvestorId] =
      str.toLongOption.map(InvestorId(_))
  }
}
