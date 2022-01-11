package io.github.riblestrype.portfolio

import cats.FlatMap
import cats.syntax.all._
import io.github.riblestrype.portfolio.Assets.{Asset, USD}
import io.github.riblestrype.portfolio.Investments.{Investment, InvestorId}
import io.github.riblestrype.portfolio.Portfolios.Portfolio

trait Portfolios[F[_]] {
  def of(investor: InvestorId): F[Portfolio]
}

object Portfolios {

  final case class ValuedAsset(
      symbol: Assets.Symbol,
      name: Assets.Name,
      usdPrice: Assets.USD,
      amount: Investments.Amount
  ) {
    def subTotal: USD = USD(amount.value * usdPrice.value)
  }
  object ValuedAsset {
    def apply(asset: Asset, investment: Investment): ValuedAsset =
      ValuedAsset(asset.symbol, asset.name, asset.usdPrice, investment.amount)
  }

  final case class ValuedAssets(
      assets: List[ValuedAsset]
  ) {
    def total: USD = assets.foldMap(_.subTotal)
  }

  final case class Portfolio(
      valuedAssets: ValuedAssets,
      unvaluedAssets: List[Investment]
  )
  object Portfolio {
    val empty: Portfolio = Portfolio(
      valuedAssets = ValuedAssets(List.empty),
      unvaluedAssets = List.empty
    )
  }

  def of[F[_]: FlatMap](assets: Assets[F], investments: Investments[F]): Portfolios[F] =
    investorId =>
      for {
        investments <- investments.fetch(investorId)
        assets      <- assets.fetch(investments.map(_.asset))
        assetsBySymbol = assets.map(a => a.symbol -> a).toMap
      } yield investments.foldLeft(Portfolio.empty) {
        case (portfolio, investment) =>
          assetsBySymbol
            .get(investment.asset)
            .map { asset =>
              portfolio.copy(
                valuedAssets = portfolio.valuedAssets.copy(
                  assets = ValuedAsset(asset, investment) :: portfolio.valuedAssets.assets
                )
              )
            }
            .getOrElse(
              portfolio.copy(
                unvaluedAssets = investment :: portfolio.unvaluedAssets
              )
            )
      }
}
