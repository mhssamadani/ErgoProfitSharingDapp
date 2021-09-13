package staking

import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, ErgoContract, ErgoId}
import helpers.{Configs, Utils}

object Contracts {
  lazy val income: ErgoContract = generateIncomeContract()

  private def generateIncomeContract(): ErgoContract ={
    Configs.ergoClient.execute(ctx => {
      val income = ctx.compileContract(ConstantsBuilder.create()
        .item("maxFee", Configs.maxFee)
        .build(), Scripts.incomeScript)
      val address = Utils.getContractAddress(income)
      println(s"income address is : \t\t\t$address")
      income
    })
  }

}

