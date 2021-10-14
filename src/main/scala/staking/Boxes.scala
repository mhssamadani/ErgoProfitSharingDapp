package staking

import java.math.BigInteger

import helpers.{Configs, Utils}
import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.{Address, ErgoId, ErgoToken, ErgoValue, InputBox, OutBox, UnsignedTransactionBuilder}
import org.ergoplatform.appkit.impl.ErgoTreeContract

object Boxes {
  def get10Income(withToken: Boolean= false): Vector[InputBox] ={
    var incomeInputBoxes: Vector[InputBox] = Vector()
    Configs.ergoClient.execute(ctx => {
      val txB = ctx.newTxBuilder()
      val tokenId = Utils.randomId()
      for (i <- 0 until 10) {
        val box = txB.outBoxBuilder()
          .value((10 * 1e9).toLong)
          .contract(Contracts.income)
        if (withToken) box.tokens(new ErgoToken(tokenId, Utils.randLong(10, 20)))
        incomeInputBoxes = incomeInputBoxes :+ box.build().convertToInputWith(Utils.randomId(), 0)
      }
      incomeInputBoxes
    })
  }

  def getIncome(txB: UnsignedTransactionBuilder, value: Long, tokenCount: Long = 0, tokenId: String = ""): OutBox ={
    val box = txB.outBoxBuilder()
      .value(value)
      .contract(Contracts.income)
    if (tokenCount > 0) box.tokens(new ErgoToken(tokenId, tokenCount))
    box.build()
  }

  def getTicket(txB: UnsignedTransactionBuilder, value: Long, stake: Long, address: ErgoAddress,  r4: Array[Long]
                , reservedTokenId: ErgoId): OutBox ={
    txB.outBoxBuilder()
      .value(value)
      .contract(Contracts.ticket)
      .tokens(new ErgoToken(Configs.token.locking, 1), new ErgoToken(Configs.token.staking, stake))
      .registers(Utils.longListToErgoValue(r4),
        ErgoValue.of(new ErgoTreeContract(address.script).getErgoTree.bytes),
        ErgoValue.of(reservedTokenId.getBytes))
      .build()
  }

  def getConfig(distCount: Long, lockingCount: Long, r4: Array[Long]): OutBox ={
    Configs.ergoClient.execute(ctx => {
      val txB = ctx.newTxBuilder()
      txB.outBoxBuilder()
        .value(1e9.toLong)
        .contract(Contracts.config)
        .tokens(new ErgoToken(Configs.token.configNFT, 1),
          new ErgoToken(Configs.token.distribution, distCount),
          new ErgoToken(Configs.token.locking, lockingCount))
        .registers(Utils.longListToErgoValue(r4))
        .build()
    })
  }

  def getDistribution(txB: UnsignedTransactionBuilder, value: Long, checkpoint: Long, fee: Long, ticketCount: Long,
                      ergShare: Long, tokenShare: Long = 0, tokenCount: Long = 0, tokenId: String = ""): OutBox ={
    var box = txB.outBoxBuilder()
      .value(value)
      .contract(Contracts.distribution)
      .registers(Utils.longListToErgoValue(Array(checkpoint, ergShare, tokenShare, fee)), ErgoValue.of(ticketCount))
    if(tokenCount== 0) box = box.tokens(new ErgoToken(Configs.token.distribution, 1))
    else box = box.tokens(new ErgoToken(Configs.token.distribution, 1),
      new ErgoToken(tokenId, tokenCount))
    box.build()
  }

  def getStakeTokenBox(stakeCount: Long, value: Long, address: Address): InputBox ={
    Configs.ergoClient.execute(ctx => {
      val txB = ctx.newTxBuilder()
      txB.outBoxBuilder()
        .value(value)
        .contract(new ErgoTreeContract(address.getErgoAddress.script))
        .tokens(new ErgoToken(Configs.token.staking, stakeCount))
        .build()
        .convertToInputWith(Utils.randomId(), 0)
    })
  }

}
