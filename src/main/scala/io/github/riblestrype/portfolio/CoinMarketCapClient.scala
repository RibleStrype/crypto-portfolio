package io.github.riblestrype.portfolio

import cats.effect.MonadCancelThrow
import cats.effect.kernel.{Async, Resource}
import cats.syntax.all._
import io.circe.Decoder
import io.circe.generic.semiauto.deriveDecoder
import io.estatico.newtype.macros.newtype
import io.github.riblestrype.portfolio.Assets._
import org.http4s.Method.GET
import org.http4s.circe.JsonDecoder
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.Accept
import org.http4s.implicits._
import org.http4s.{Header, MediaType, Uri}
import org.typelevel.ci.CIString

import scala.util.control.NoStackTrace

trait CoinMarketCapClient[F[_]] {
  def latestListing: F[List[Asset]]
}

object CoinMarketCapClient {

  private object Protocol {
    val baseUri          = uri"https://pro-api.coinmarketcap.com/v1"
    val apiKeyHeaderName = CIString("X-CMC_PRO_API_KEY")

    final case class Status(errorCode: Int, errorMessage: String)

    implicit val statusDecoder: Decoder[Status] = c =>
      for {
        errorCode    <- c.get[Int]("error_code")
        errorMessage <- c.get[String]("error_message")
      } yield Status(errorCode, errorMessage)

    implicit val assetDecoder: Decoder[Asset] = c =>
      for {
        name     <- c.get[Name]("name")
        symbol   <- c.get[Symbol]("symbol")
        usdPrice <- c.downField("quote").downField("USD").get[USD]("price")
      } yield Asset(symbol, name, usdPrice)

    final case class SuccessfulResponse[T](data: T)
    final case class ErrorResponse(status: Status)

    implicit val errorResponseDecoder: Decoder[ErrorResponse] = deriveDecoder
    implicit def successfulResponseDecoder[T: Decoder]: Decoder[SuccessfulResponse[T]] =
      deriveDecoder
  }

  @newtype case class ApiKey(value: String)

  final case class ApiError(message: String) extends Exception(message) with NoStackTrace

  import Protocol._

  def of[F[_]: Async](apiKey: ApiKey): Resource[F, CoinMarketCapClient[F]] =
    EmberClientBuilder.default[F].build.map(of(baseUri, apiKey))

  private def of[F[_]: MonadCancelThrow: JsonDecoder](
      baseUri: Uri,
      apiKey: ApiKey
  )(client: Client[F]): CoinMarketCapClient[F] =
    new CoinMarketCapClient[F] with Http4sClientDsl[F] {
      val latestListingUri: Uri = baseUri / "cryptocurrency" / "listings" / "latest"

      override def latestListing: F[List[Asset]] = {
        val req = GET(
          latestListingUri.withQueryParam("limit", 99),
          Accept(MediaType.application.json),
          Header.Raw(apiKeyHeaderName, apiKey.value)
        )
        client.run(req).use { res =>
          val dec = JsonDecoder[F]
          if (res.status.isSuccess)
            dec.asJsonDecode[SuccessfulResponse[List[Asset]]](res).map(_.data)
          else
            dec.asJsonDecode[ErrorResponse](res).flatMap { err =>
              ApiError(
                s"Failed to fetch latest listings, ${err.status.errorCode}: ${err.status.errorMessage}"
              ).raiseError
            }
        }
      }
    }
}
