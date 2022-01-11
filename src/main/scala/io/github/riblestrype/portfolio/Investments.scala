package io.github.riblestrype.portfolio

import cats.effect.{MonadCancelThrow, Resource}
import cats.syntax.all._
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.{Decoder, Encoder}
import io.estatico.newtype.macros.newtype
import io.github.riblestrype.portfolio.Assets.Symbol
import io.github.riblestrype.portfolio.Investments.{Investment, InvestorId}
import skunk._
import skunk.codec.all._
import skunk.implicits._

trait Investments[F[_]] {
  def save(investorId: InvestorId, investment: Investment): F[Unit]
  def fetch(investorId: InvestorId): F[List[Investment]]
}

object Investments {

  @newtype case class InvestorId(value: Long)

  @newtype case class Amount(value: BigDecimal)
  object Amount {
    implicit val encoder: Encoder[Amount] = Encoder.encodeBigDecimal.contramap(_.value)
    implicit val decoder: Decoder[Amount] = Decoder.decodeBigDecimal.map(Amount(_))
  }

  final case class Investment(asset: Symbol, amount: Amount)
  object Investment {
    implicit val decoder: Decoder[Investment] = deriveDecoder
    implicit val encoder: Encoder[Investment] = deriveEncoder
  }

  def of[F[_]: MonadCancelThrow](
      sessions: Resource[F, Session[F]]
  )(implicit C: fs2.Compiler[F, F]): Investments[F] =
    new Investments[F] {
      val investorId = int8.imap(InvestorId(_))(_.value)
      val symbol     = varchar.imap(Symbol(_))(_.value)
      val amount     = numeric.imap(Amount(_))(_.value)
      val investment = (symbol ~ amount).gimap[Investment]

      val insertCmd: Command[InvestorId ~ Investment] =
        sql"""
             INSERT INTO investments(investor_id, asset, amount) 
             VALUES($investorId, $investment)
             ON CONFLICT (investor_id, asset) DO UPDATE
             SET amount = EXCLUDED.amount
           """.command

      val select: Query[InvestorId, Investment] =
        sql"""
             SELECT asset, amount FROM investments WHERE investor_id = $investorId
           """.query(investment)

      override def save(investorId: InvestorId, investment: Investment): F[Unit] =
        sessions.use { sess =>
          sess.prepare(insertCmd).use { pc =>
            pc.execute(investorId, investment).void
          }
        }

      override def fetch(investorId: InvestorId): F[List[Investment]] =
        sessions.use { sess =>
          sess.prepare(select).use { pq =>
            pq.stream(investorId, 128).compile.toList
          }
        }
    }
}
