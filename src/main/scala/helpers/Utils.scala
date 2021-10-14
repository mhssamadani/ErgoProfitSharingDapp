package helpers

import org.ergoplatform.ErgoAddress
import org.ergoplatform.appkit.{ErgoContract, ErgoType, ErgoValue, JavaHelpers}
import scorex.crypto.hash.Digest32
import sigmastate.serialization.ErgoTreeSerializer
import special.collection.Coll

import scala.util.Random

object Utils {
  private val random = Random

  def getContractAddress(contract: ErgoContract): String = {
    val ergoTree = contract.getErgoTree
    Configs.addressEncoder.fromProposition(ergoTree).get.toString
  }

  def getContractScriptHash(contract: ErgoContract): Digest32 = {
    scorex.crypto.hash.Blake2b256(contract.getErgoTree.bytes)
  }

  def longListToErgoValue(elements: Array[Long]): ErgoValue[Coll[Long]] = {
    val longColl = JavaHelpers.SigmaDsl.Colls.fromArray(elements)
    ErgoValue.of(longColl, ErgoType.longType())
  }

  def getAddress(addressBytes: Array[Byte]): ErgoAddress = {
    val ergoTree = ErgoTreeSerializer.DefaultSerializer.deserializeErgoTree(addressBytes)
    Configs.addressEncoder.fromProposition(ergoTree).get
  }

  def randDouble: Double = random.nextDouble()
  def randLong(min: Long, max:Long): Long ={
    val range = max - min
    (randDouble * range).toLong + min
  }

  def randomId(): String ={
    val randomBytes = Array.fill(32)((scala.util.Random.nextInt(256) - 128).toByte)
    randomBytes.map("%02x" format _).mkString
  }

}
