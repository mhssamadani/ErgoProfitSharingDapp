package staking

import helpers.Configs
import org.ergoplatform.appkit.{ErgoToken, InputBox}

import scala.collection.JavaConverters._

object Main {
  def incomeMerge(): Unit ={
    Configs.ergoClient.execute(ctx => {
      val txB = ctx.newTxBuilder()
      var incomeInputBoxes: Vector[InputBox] = Vector()
      var value: Long = 0
      for (i <- 0 until 10) {
        value += ((i+1)*1e9).toLong
        incomeInputBoxes = incomeInputBoxes :+ txB.outBoxBuilder()
          .value(((i+1)*1e9).toLong)
          .contract(Contracts.income)
//          .tokens(new ErgoToken("011d3364de07e5a26f0c4eef0852cddb387039a921b7154ef3cab22c6eda887f", 10))
          .build()
          .convertToInputWith("0ae0e252b661c8018bced13488335556351a44a97267296844912a526d275164", i.toShort)
      }
      val outIncome = txB.outBoxBuilder()
        .value(value- Configs.fee)
//        .tokens(new ErgoToken("011d3364de07e5a26f0c4eef0852cddb387039a921b7154ef3cab22c6eda887f", 100))
        .contract(Contracts.income)
        .build()

      val tx = txB.boxesToSpend(incomeInputBoxes.asJava)
        .fee(Configs.fee)
        .outputs(outIncome)
        .sendChangeTo(Configs.idleAddress.getErgoAddress)
        .build()

      val prover = ctx.newProverBuilder().build()
      val signedTx = prover.sign(tx)
      println("Incomes merged successfully")
      println(signedTx.toJson(false))
    })
  }
  def main(args: Array[String]): Unit = {
    incomeMerge()
  }
}
