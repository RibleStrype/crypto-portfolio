package io.github.riblestrype.portfolio

import cats.effect.{MonadCancelThrow, Resource}
import cats.kernel.Monoid
import cats.syntax.all._
import io.circe.{Decoder, Encoder}
import io.estatico.newtype.macros.newtype
import io.github.riblestrype.portfolio.Assets._
import skunk.codec.all._
import skunk.implicits._
import skunk.{Command, Query, Session}

trait Assets[F[_]] {
  def save(assets: List[Asset]): F[Unit]
  def fetch(symbols: List[Symbol]): F[List[Asset]]
}

object Assets {

  @newtype case class Symbol(value: String)
  object Symbol {
    implicit val symbolEncoder: Encoder[Symbol] = Encoder.encodeString.contramap(_.value)
    implicit val symbolDecoder: Decoder[Symbol] = Decoder.decodeString.map(Symbol(_))
  }

  @newtype case class Name(value: String)
  object Name {
    implicit val nameEncoder: Encoder[Name] = Encoder.encodeString.contramap(_.value)
    implicit val nameDecoder: Decoder[Name] = Decoder.decodeString.map(Name(_))
  }

  @newtype case class USD(value: BigDecimal)
  object USD {
    implicit val usdEncoder: Encoder[USD] = Encoder.encodeBigDecimal.contramap(_.value)
    implicit val usdDecoder: Decoder[USD] = Decoder.decodeBigDecimal.map(USD(_))
    implicit val monoid: Monoid[USD] = new Monoid[USD] {
      override def empty: USD = USD(0)

      override def combine(x: USD, y: USD): USD =
        USD(x.value + y.value)
    }
  }

  final case class Asset(symbol: Symbol, name: Name, usdPrice: USD)

  def of[F[_]: MonadCancelThrow](
      sessions: Resource[F, Session[F]]
  )(implicit C: fs2.Compiler[F, F]): Assets[F] =
    new Assets[F] {
      val symbol = varchar.imap(Symbol(_))(_.value)
      val name   = varchar.imap(Name(_))(_.value)
      val price  = numeric.imap(USD(_))(_.value)
      val asset  = (symbol ~ name ~ price).gimap[Asset]

      def insertMany(n: Int): Command[List[Asset]] = {
        val enc = asset.values.list(n)
        sql"INSERT INTO assets VALUES $enc ON CONFLICT (symbol) DO UPDATE SET price_usd = EXCLUDED.price_usd".command
      }

      def select(n: Int): Query[List[Symbol], Asset] = {
        val enc = symbol.values.list(n)
        sql"SELECT * FROM assets WHERE symbol IN($enc)".query(asset)
      }

      override def save(assets: List[Asset]): F[Unit] =
        sessions.use { sess =>
          sess.prepare(insertMany(assets.size)).use { pc =>
            pc.execute(assets).void
          }
        }

      override def fetch(symbols: List[Symbol]): F[List[Asset]] =
        if (symbols.isEmpty)
          List.empty[Asset].pure
        else
          sessions
            .use { sess =>
              sess.prepare(select(symbols.size)).use { pq =>
                pq.stream(symbols, 128).compile.toList
              }
            }
    }
}
