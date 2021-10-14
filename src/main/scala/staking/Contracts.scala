package staking

import org.ergoplatform.appkit.{BlockchainContext, ConstantsBuilder, ErgoContract, ErgoId}
import helpers.{Configs, Utils}

object Contracts {
  lazy val income: ErgoContract = generateIncomeContract()
  lazy val distribution: ErgoContract = generateDistributionContract()
  lazy val ticket: ErgoContract = generateTicketContract()
  lazy val config: ErgoContract = generateConfigContract()

  private def generateIncomeContract(): ErgoContract ={
    Configs.ergoClient.execute(ctx => {
      val income = ctx.compileContract(ConstantsBuilder.create()
        .item("maxFee", Configs.maxFee)
        .item("configNFT", ErgoId.create(Configs.token.configNFT).getBytes)
        .build(), Scripts.incomeScript)
      val address = Utils.getContractAddress(income)
      println(s"income address is : \t\t\t$address")
      income
    })
  }

  private def generateDistributionContract(): ErgoContract ={
    Configs.ergoClient.execute(ctx => {
      val contract = ctx.compileContract(ConstantsBuilder.create()
        .item("configNFT", ErgoId.create(Configs.token.configNFT).getBytes)
        .item("lockingToken", ErgoId.create(Configs.token.locking).getBytes)
        .item("stakingToken", ErgoId.create(Configs.token.staking).getBytes)
        .build(), Scripts.distributionScript)
      val address = Utils.getContractAddress(contract)
      println(s"distribution address is : \t\t\t$address")
      contract
    })
  }

  private def generateTicketContract(): ErgoContract ={
    Configs.ergoClient.execute(ctx => {
      val contract = ctx.compileContract(ConstantsBuilder.create()
        .item("configNFT", ErgoId.create(Configs.token.configNFT).getBytes)
        .item("distributionToken", ErgoId.create(Configs.token.distribution).getBytes)
        .build(), Scripts.ticketScript)
      val address = Utils.getContractAddress(contract)
      println(s"ticket address is : \t\t\t$address")
      contract
    })
  }

  private def generateConfigContract(): ErgoContract ={
    Configs.ergoClient.execute(ctx => {
      val contract = ctx.compileContract(ConstantsBuilder.create()
        .item("distributionToken", ErgoId.create(Configs.token.distribution).getBytes)
        .item("lockingToken", ErgoId.create(Configs.token.locking).getBytes)
        .item("stakingToken", ErgoId.create(Configs.token.staking).getBytes)
        .item("distributionHash", Utils.getContractScriptHash(distribution))
        .item("ticketHash", Utils.getContractScriptHash(ticket))
        .build(), Scripts.configScript)
      val address = Utils.getContractAddress(contract)
      println(s"config address is : \t\t\t$address")
      contract
    })
  }

}

