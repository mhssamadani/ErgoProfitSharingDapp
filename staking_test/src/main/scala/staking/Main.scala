package staking

import java.math.BigInteger

import helpers.{Configs, Utils}
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.{Address, ErgoId, ErgoToken, InputBox, OutBox, SignedTransaction}
import special.collection.Coll

import scala.collection.JavaConverters._

object Main {
  def incomeMerge(incomeInputBoxes: Vector[InputBox]): SignedTransaction = {
    Configs.ergoClient.execute(ctx => {
      val txB = ctx.newTxBuilder()

      val totalValue: Long = incomeInputBoxes.map(item => item.getValue).reduce((a, b) => a + b)
      var outIncome = txB.outBoxBuilder()
        .value(totalValue - Configs.fee)
        .contract(Contracts.income)

      if(incomeInputBoxes(0).getTokens.size() > 0) {
        val tokenId: String = incomeInputBoxes(0).getTokens.get(0).getId.toString
        val totalTokens: Long = incomeInputBoxes.map(item => item.getTokens.get(0).getValue).sum
        outIncome = outIncome.tokens(new ErgoToken(tokenId, totalTokens))
      }
      val outIncomeBox = outIncome.build()

      val tx = txB.boxesToSpend(incomeInputBoxes.asJava)
        .fee(Configs.fee)
        .outputs(outIncomeBox)
        .sendChangeTo(Configs.idleAddress.getErgoAddress)
        .build()

      val prover = ctx.newProverBuilder().build()
      val signedTx = prover.sign(tx)
      println("Incomes merged successfully")
      signedTx
    })
  }

  def distributionPayment(bank: InputBox, ticket: InputBox): SignedTransaction ={
    Configs.ergoClient.execute(ctx =>{
      val txB = ctx.newTxBuilder()
      val r4 = bank.getRegisters.get(0).getValue.asInstanceOf[Coll[Long]].toArray
      val checkpoint = r4(0)
      val ergShare = r4(1)
      val tokenShare = r4(2)
      val fee = r4(3)
      val stakeCount = ticket.getTokens.get(1).getValue
      val ticketAddress = Utils.getAddress(ticket.getRegisters.get(1).getValue.asInstanceOf[Coll[Byte]].toArray)
      val initialCheckpoint = ticket.getRegisters.get(0).getValue.asInstanceOf[Coll[Long]].toArray(0)
      val reservedToken: ErgoId = new ErgoId(ticket.getRegisters.get(2).getValue.asInstanceOf[Coll[Byte]].toArray)

      var bankOut: OutBox = null
      var payment: OutBox = null
      if(tokenShare == 0){
        bankOut = Boxes.getDistribution(txB, bank.getValue - ergShare*stakeCount - fee, checkpoint, fee, ergShare)
        payment = txB.outBoxBuilder()
          .value(ergShare*stakeCount)
          .contract(new ErgoTreeContract(ticketAddress.script))
          .build()
      }
      else{
        val tokenPayment = tokenShare * stakeCount
        val tokenCount = bank.getTokens.get(0).getValue - tokenPayment
        val tokenId = bank.getTokens.get(0).getId.toString
        bankOut = Boxes.getDistribution(txB, bank.getValue - ergShare*stakeCount - fee, checkpoint, fee, ergShare, tokenShare, tokenCount, tokenId)
        payment = txB.outBoxBuilder()
          .value(ergShare*stakeCount)
          .contract(new ErgoTreeContract(ticketAddress.script))
          .tokens(new ErgoToken(tokenId, tokenCount))
          .build()
      }
      val ticketOut = Boxes.getTicket(txB, ticket.getValue, stakeCount, ticketAddress, checkpoint+1, initialCheckpoint, reservedToken)

      val tx = txB.boxesToSpend(Seq(bank, ticket).asJava)
        .fee(Configs.fee)
        .outputs(bankOut, ticketOut, payment)
        .sendChangeTo(Configs.idleAddress.getErgoAddress)
        .build()

      val prover = ctx.newProverBuilder().build()
      val signedTx = prover.sign(tx)
      println("distribution tx completed successfully")
//      println(signedTx.toJson(false))
      signedTx
    })
  }

  def distributionRedeem(config: InputBox, bank: InputBox): SignedTransaction ={
    Configs.ergoClient.execute(ctx => {
      val txB = ctx.newTxBuilder()
      val r4 = config.getRegisters.get(0).getValue.asInstanceOf[Coll[Long]].toArray
      val fee = r4(5)
      val configOut: OutBox = Boxes.getConfig(config.getTokens.get(1).getValue, config.getTokens.get(2).getValue + 1, r4)

      val tx = txB.boxesToSpend(Seq(config, bank).asJava)
        .fee(fee)
        .outputs(configOut)
        .sendChangeTo(Configs.idleAddress.getErgoAddress)
        .build()

      val prover = ctx.newProverBuilder().build()
      val signedTx = prover.sign(tx)
      println("distribution redeem tx completed successfully")
      signedTx
    })
  }

  def ticketUnlocking(config: InputBox, ticket: InputBox, reservedTokenBox: InputBox, secret: BigInteger): SignedTransaction = {
    Configs.ergoClient.execute(ctx => {
      val txB = ctx.newTxBuilder()
      val r4 = config.getRegisters.get(0).getValue.asInstanceOf[Coll[Long]].toArray
      val fee = r4(5)
      val checkpoint = r4(0)
      val stakeCount = ticket.getTokens.get(1).getValue
      val ticketAddress = Utils.getAddress(ticket.getRegisters.get(1).getValue.asInstanceOf[Coll[Byte]].toArray)
      val initialCheckpoint = ticket.getRegisters.get(0).getValue.asInstanceOf[Coll[Long]].toArray(0)
      val ticketCheckpoint = ticket.getRegisters.get(0).getValue.asInstanceOf[Coll[Long]].toArray(1)
      if(checkpoint == initialCheckpoint) throw new Throwable("the ticket must get at least one income")
      if(checkpoint > ticketCheckpoint) throw new Throwable("The ticket is not ready for unlocking, it did'nt received some incomes")

      val configOut: OutBox = Boxes.getConfig(config.getTokens.get(1).getValue + 1, config.getTokens.get(2).getValue, r4)
      val payment = txB.outBoxBuilder()
        .value(ticket.getValue - fee)
        .contract(new ErgoTreeContract(ticketAddress.script))
        .tokens(new ErgoToken(Configs.token.staking, stakeCount))
        .build()

      val tx = txB.boxesToSpend(Seq(config, ticket, reservedTokenBox).asJava)
        .fee(fee)
        .outputs(configOut, payment)
        .sendChangeTo(Configs.idleAddress.getErgoAddress)
        .tokensToBurn(reservedTokenBox.getTokens.get(0))
        .build()

      val prover = ctx.newProverBuilder()
        .withDLogSecret(secret)
        .build()
      val signedTx = prover.sign(tx)
      println("Ticket unlocking tx completed successfully")
      signedTx
    })
  }

  def distributionCreation(income: InputBox, config: InputBox): SignedTransaction ={
    Configs.ergoClient.execute(ctx => {
      val txB = ctx.newTxBuilder()
      val r4 = config.getRegisters.get(0).getValue.asInstanceOf[Coll[Long]].toArray
      val checkpoint = r4(0)
      val minErgShare = r4(1)
      val minTokenShare = r4(2)
      val ticketCount = r4(3)
      val stakeCount = r4(4)
      val fee = r4(5)
      val distFee = (ticketCount+2) * fee

      var outDistribution: OutBox = null
      var outIncome: OutBox = null
      if(stakeCount * minErgShare + distFee <= income.getValue && income.getTokens.size() == 0){
        val ergShare = ((income.getValue - distFee) / stakeCount).toLong
        val distValue = (ergShare * stakeCount) + distFee
        outDistribution = Boxes.getDistribution(txB, distValue - fee, checkpoint, fee, ergShare)
        outIncome = Boxes.getIncome(txB, income.getValue - distValue)
      }
      else if(distFee >= income.getValue && stakeCount * minTokenShare <= income.getTokens.get(0).getValue){
        val ergShare = ((income.getValue - distFee) / stakeCount).toLong
        val distValue = (ergShare * stakeCount) + distFee
        val tokenShare = (income.getTokens.get(0).getValue / stakeCount).toLong
        val tokenCount = tokenShare * ergShare
        val tokenId = income.getTokens.get(0).getId.toString
        outDistribution = Boxes.getDistribution(txB, distValue - fee, checkpoint, fee, ergShare, tokenShare, tokenCount, tokenId)
        outIncome = Boxes.getIncome(txB, income.getValue - distValue, income.getTokens.get(0).getValue - tokenCount, tokenId)
      }
      else{
        println("stakeCount * minErgShare:", stakeCount * minErgShare)
        println("distFee:", distFee)
        println("income:", income.getValue)
        throw new Exception("more funds is required")
      }

      val outConfig = Boxes.getConfig(config.getTokens.get(1).getValue, config.getTokens.get(2).getValue - 1,
        Array(checkpoint+1, minErgShare, minTokenShare, ticketCount, stakeCount, fee))

      val tx = txB.boxesToSpend(Seq(config, income).asJava)
        .fee(Configs.fee)
        .outputs(outConfig, outDistribution, outIncome)
        .sendChangeTo(Configs.idleAddress.getErgoAddress)
        .build()

      val prover = ctx.newProverBuilder().build()
      val signedTx = prover.sign(tx)
      println("distribution box created successfully")
      signedTx
    })
  }

  def lockingStakingToken(config: InputBox, stakingTokensBox: InputBox, ownerAddress: Address, ownerSecret: BigInteger): SignedTransaction ={
    Configs.ergoClient.execute(ctx => {
      val txB = ctx.newTxBuilder()
      val r4 = config.getRegisters.get(0).getValue.asInstanceOf[Coll[Long]].toArray
      val checkpoint = r4(0)
      val minErgShare = r4(1)
      val minTokenShare = r4(2)
      val ticketCount = r4(3)
      val stakeCount = r4(4)
      val fee = r4(5)
      val newStakeCount = stakingTokensBox.getTokens.get(0).getValue

      val outConfig = Boxes.getConfig(config.getTokens.get(1).getValue - 1, config.getTokens.get(2).getValue,
        Array(checkpoint, minErgShare, minTokenShare, ticketCount+1, stakeCount+ newStakeCount, fee))
      val outTicket = Boxes.getTicket(txB, fee, newStakeCount, ownerAddress.getErgoAddress, checkpoint, checkpoint, config.getId)
      val outReservedToken = txB.outBoxBuilder()
        .value(fee)
        .contract(new ErgoTreeContract(ownerAddress.getErgoAddress.script))
//      # TODO: use registers to name the reserve token
//        .registers()
        .tokens(new ErgoToken(config.getId, 1))
        .build()

      val tx = txB.boxesToSpend(Seq(config, stakingTokensBox).asJava)
        .fee(Configs.fee)
        .outputs(outConfig, outTicket, outReservedToken)
        .sendChangeTo(Configs.idleAddress.getErgoAddress)
        .build()

      val prover = ctx.newProverBuilder()
        .withDLogSecret(ownerSecret)
        .build()
      val signedTx = prover.sign(tx)
      println("staking tokens locked successfully")
      signedTx
    })
  }

  def main(args: Array[String]): Unit = {
    val initialConfig = Boxes.getConfig(1000, 1000, Array(200, (0.1 * 1e9).toLong, 1, 0, 0, Configs.fee)).convertToInputWith(Utils.randomId(), 0)
    val initialIncomes = Boxes.get10Income()
    val initialStakeTokenBox = Boxes.getStakeTokenBox(10, Configs.fee*3, Configs.address1)
    val incomeTx = incomeMerge(initialIncomes)
    val lockingTx = lockingStakingToken(initialConfig, initialStakeTokenBox, Configs.address1, Configs.secret1)
    val distributionTx = distributionCreation(incomeTx.getOutputsToSpend.get(0), lockingTx.getOutputsToSpend.get(0))
    val distributionPaymentTx = distributionPayment(distributionTx.getOutputsToSpend.get(1), lockingTx.getOutputsToSpend.get(1))
    val distributionRedeemTx = distributionRedeem(distributionTx.getOutputsToSpend.get(0), distributionPaymentTx.getOutputsToSpend.get(0))
    val unlockTx = ticketUnlocking(distributionRedeemTx.getOutputsToSpend.get(0), distributionPaymentTx.getOutputsToSpend.get(1),
      lockingTx.getOutputsToSpend.get(2), Configs.secret1)
  }
}



