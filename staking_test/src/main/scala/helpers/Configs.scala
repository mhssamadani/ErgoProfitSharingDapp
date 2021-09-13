package helpers

import com.typesafe.config.{Config, ConfigFactory}
import org.ergoplatform.ErgoAddressEncoder
import org.ergoplatform.appkit.{Address, ErgoClient, NetworkType, RestApiErgoClient}

trait ConfigHelper {
  val config: Config  = ConfigFactory.load()

  /**
   * Read the config and return the value of the key
   *
   * @param key     key to find
   * @param default default value if the key is not found
   * @return value of the key
   */
  def readKey(key: String, default: String = null): String = {
    try {
      config.getString(key)
    } catch {
      case ex: Throwable =>
        println(s"${key} is required.")
        sys.exit()
    }
  }
}

object Configs extends ConfigHelper {
  object node {
    lazy val apiKey: String = readKey("node.apiKey")
    lazy val url: String = readKey("node.url")
    lazy val networkType: NetworkType = if (readKey("node.networkType").toLowerCase.equals("mainnet")) NetworkType.MAINNET else NetworkType.TESTNET
  }
  private lazy val explorerUrlConf = readKey("explorer.url", "")
  lazy val explorer: String = if (explorerUrlConf.isEmpty) RestApiErgoClient.getDefaultExplorerUrl(node.networkType) else explorerUrlConf
  lazy val fee: Long = readKey("fee.default", "1000000").toLong
  lazy val maxFee: Long = readKey("fee.max", "1000000").toLong
  val ergoClient: ErgoClient = RestApiErgoClient.create(node.url, node.networkType, node.apiKey, explorer)
  lazy val addressEncoder = new ErgoAddressEncoder(node.networkType.networkPrefix)

  val idleAddress: Address = Address.create("9fUpyNAvdaCaXLGLgufVdcL7KRGUQgmtu31QxexHmxL1GyHixXd")
}
