package io.github.riblestrype.portfolio

import cats.FlatMap
import cats.effect.kernel.Temporal
import cats.syntax.all._
import org.typelevel.log4cats.Logger

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object AssetSync {

  private val delay: FiniteDuration = 30.seconds

  def start[F[_]: FlatMap: Temporal: Logger](
      assets: Assets[F],
      client: CoinMarketCapClient[F]
  ): F[Unit] =
    (fs2.Stream.emit(()) ++ fs2.Stream.fixedDelay(delay))
      .evalMap { _ =>
        for {
          as <- client.latestListing
          _  <- assets.save(as)
          _  <- Logger[F].info(s"${as.size} crypto assets updated.")
        } yield ()
      }
      .attempts(fs2.Stream.constant(delay))
      .evalMap {
        case Left(t)  => Logger[F].error(t)("Failed to update assets.")
        case Right(_) => ().pure
      }
      .compile
      .drain
}
