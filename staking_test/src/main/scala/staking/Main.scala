package staking

import java.math.BigInteger

import helpers.{Configs, Utils}
import org.ergoplatform.appkit.impl.ErgoTreeContract
import org.ergoplatform.appkit.{Address, ErgoId, ErgoToken, InputBox, OutBox, SignedTransaction, UnsignedTransaction}
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
      val ticketCount = bank.getRegisters.get(1).getValue.asInstanceOf[Long]
      val stakeCount = ticket.getTokens.get(1).getValue
      val ticketAddress = Utils.getAddress(ticket.getRegisters.get(1).getValue.asInstanceOf[Coll[Byte]].toArray)
      val ticketR4 = ticket.getRegisters.get(0).getValue.asInstanceOf[Coll[Long]].toArray
      val ticketFee = ticketR4(2)
      val ticketMinBoxVal = ticketR4(3)
      val reservedToken: ErgoId = new ErgoId(ticket.getRegisters.get(2).getValue.asInstanceOf[Coll[Byte]].toArray)

      var bankOut: OutBox = null
      var payment: OutBox = null
      if(tokenShare == 0){
        bankOut = Boxes.getDistribution(txB, bank.getValue - ergShare*stakeCount, checkpoint, fee, ticketCount-1, ergShare)
        payment = txB.outBoxBuilder()
          .value(ergShare*stakeCount + ticketMinBoxVal)
          .contract(new ErgoTreeContract(ticketAddress.script))
          .build()
      }
      else{
        val tokenPayment = tokenShare * stakeCount
        val tokenCount = bank.getTokens.get(1).getValue - tokenPayment
        val tokenId = bank.getTokens.get(1).getId.toString
        bankOut = Boxes.getDistribution(txB, bank.getValue - ergShare*stakeCount,
          checkpoint, fee, ticketCount-1, ergShare, tokenShare, tokenCount, tokenId)
        payment = txB.outBoxBuilder()
          .value(ergShare*stakeCount + ticketMinBoxVal)
          .contract(new ErgoTreeContract(ticketAddress.script))
          .tokens(new ErgoToken(tokenId, tokenPayment))
          .build()
      }
      val ticketOut = Boxes.getTicket(txB, ticket.getValue - ticketFee - ticketMinBoxVal, stakeCount, ticketAddress,
        Array(ticketR4(0), checkpoint+1 , ticketFee, ticketMinBoxVal), reservedToken)

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
      val configOut: OutBox = Boxes.getConfig(config.getTokens.get(1).getValue + 1, config.getTokens.get(2).getValue, r4)

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
      val checkpoint = r4(0)
      val minErgShare = r4(1)
      val minTokenShare = r4(2)
      val ticketCount = r4(3)
      val allStakeCount = r4(4)
      val fee = r4(5)
      val stakeCount = ticket.getTokens.get(1).getValue
      val ticketAddress = Utils.getAddress(ticket.getRegisters.get(1).getValue.asInstanceOf[Coll[Byte]].toArray)
      val initialCheckpoint = ticket.getRegisters.get(0).getValue.asInstanceOf[Coll[Long]].toArray(0)
      val ticketCheckpoint = ticket.getRegisters.get(0).getValue.asInstanceOf[Coll[Long]].toArray(1)
      if(checkpoint == initialCheckpoint) throw new Throwable("the ticket must get at least one income")
      if(checkpoint > ticketCheckpoint) throw new Throwable("The ticket is not ready for unlocking, it did'nt received some incomes")

      val configOut: OutBox = Boxes.getConfig(config.getTokens.get(1).getValue, config.getTokens.get(2).getValue + 1,
        Array(checkpoint, minErgShare, minTokenShare, ticketCount-1, allStakeCount-stakeCount, fee, r4(6), r4(7)))
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
      val distFee = 2 * fee

      var outDistribution: OutBox = null
      var outIncome: OutBox = null

      var tokenShare: Long = 0
      var tokenRemainder: Long = 0
      var ergShare = (income.getValue - distFee) / stakeCount
      var ergRemainder = income.getValue - ((ergShare * stakeCount) + distFee)
      if(ergRemainder > 0 && ergRemainder < Configs.minBoxValue){
        ergRemainder = ergRemainder + Configs.minBoxValue
        ergShare = (income.getValue - distFee - Configs.minBoxValue) / stakeCount
      }
      if(income.getTokens.size() > 0){
        tokenShare = income.getTokens.get(0).getValue / stakeCount
        tokenRemainder = income.getTokens.get(0).getValue - tokenShare*stakeCount
        if(tokenRemainder > 0 && ergRemainder == 0){
          ergShare = ergShare - Configs.minBoxValue
          ergRemainder = income.getValue - ((ergShare * stakeCount) + distFee)
        }
      }

      if(ergShare >= minErgShare && income.getTokens.size() == 0){
        val distValue = (ergShare * stakeCount) + fee
        outDistribution = Boxes.getDistribution(txB, distValue, checkpoint, fee, ticketCount, ergShare)
        outIncome = Boxes.getIncome(txB, ergRemainder)
      }
      else if(income.getTokens.size() > 0 && (income.getValue == distFee || income.getValue >= distFee + Configs.minBoxValue)
        && tokenShare >= minTokenShare){
        val distValue = (ergShare * stakeCount) + fee
        val tokenCount = tokenShare * stakeCount
        val tokenId = income.getTokens.get(0).getId.toString
        outDistribution = Boxes.getDistribution(txB, distValue, checkpoint, fee, ticketCount, ergShare, tokenShare, tokenCount, tokenId)
        outIncome = Boxes.getIncome(txB, ergRemainder, income.getTokens.get(0).getValue - tokenCount, tokenId)
      }
      else{
        println("stakeCount * minErgShare:", stakeCount * minErgShare)
        println("distFee:", distFee)
        println("income:", income.getValue)
        throw new Exception("more funds is required")
      }

      val outConfig = Boxes.getConfig(config.getTokens.get(1).getValue - 1, config.getTokens.get(2).getValue,
        Array(checkpoint+1, minErgShare, minTokenShare, ticketCount, stakeCount, fee, r4(6), r4(7)))

      var tx: UnsignedTransaction = null
      val txBuilder = txB.boxesToSpend(Seq(config, income).asJava)
        .fee(Configs.fee)
        .sendChangeTo(Configs.idleAddress.getErgoAddress)
      if(outIncome.getValue > 0) tx = txBuilder.outputs(outConfig, outDistribution, outIncome).build()
      else tx = txBuilder.outputs(outConfig, outDistribution).build()

      val prover = ctx.newProverBuilder().build()
      val signedTx = prover.sign(tx)
      println("distribution box created successfully")
//      println(signedTx.toJson(false))
      signedTx
    })
  }

  def lockingStakingToken(config: InputBox, stakingTokensBox: InputBox, ownerAddress: Address,
                          ownerSecret: BigInteger): SignedTransaction ={
    Configs.ergoClient.execute(ctx => {
      val txB = ctx.newTxBuilder()
      val r4 = config.getRegisters.get(0).getValue.asInstanceOf[Coll[Long]].toArray
      val checkpoint = r4(0)
      val minErgShare = r4(1)
      val minTokenShare = r4(2)
      val ticketCount = r4(3)
      val stakeCount = r4(4)
      val fee = r4(5)
      val minTicketValue = r4(6)
      val newStakeCount = stakingTokensBox.getTokens.get(0).getValue

      val outConfig = Boxes.getConfig(config.getTokens.get(1).getValue, config.getTokens.get(2).getValue - 1,
        Array(checkpoint, minErgShare, minTokenShare, ticketCount+1, stakeCount+ newStakeCount, fee,
          minTicketValue, r4(7)))
      val outTicket = Boxes.getTicket(txB, minTicketValue, newStakeCount, ownerAddress.getErgoAddress,
        Array(checkpoint, checkpoint, fee, Configs.minBoxValue), config.getId)
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
        .sendChangeTo(ownerAddress.getErgoAddress)
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
    val initialConfig = Boxes.getConfig(1000, 1000, Array(200, (0.1 * 1e9).toLong, 1, 0, 0,
      Configs.fee, (0.5* 1e9).toLong, Configs.minBoxValue)).convertToInputWith(Utils.randomId(), 0)
    val initialIncomes = Boxes.get10Income(withToken = false)
    val initialStakeTokenBox = Boxes.getStakeTokenBox(10, (1e9).toLong, Configs.address1)
    val incomeTx = incomeMerge(initialIncomes)
    val lockingTx = lockingStakingToken(initialConfig, initialStakeTokenBox, Configs.address1, Configs.secret1)
    val distributionTx = distributionCreation(incomeTx.getOutputsToSpend.get(0), lockingTx.getOutputsToSpend.get(0))
    val distributionPaymentTx = distributionPayment(distributionTx.getOutputsToSpend.get(1), lockingTx.getOutputsToSpend.get(1))
    val distributionRedeemTx = distributionRedeem(distributionTx.getOutputsToSpend.get(0), distributionPaymentTx.getOutputsToSpend.get(0))
    val unlockTx = ticketUnlocking(distributionRedeemTx.getOutputsToSpend.get(0), distributionPaymentTx.getOutputsToSpend.get(1),
      lockingTx.getOutputsToSpend.get(2), Configs.secret1)
  }
}



