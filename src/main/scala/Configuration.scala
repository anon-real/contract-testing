import org.ergoplatform.ErgoAddressEncoder
import org.ergoplatform.appkit.{ErgoClient, NetworkType, RestApiErgoClient}

object Configuration {
  val ergoClient: ErgoClient = RestApiErgoClient.create("http://188.34.207.91:9053/", NetworkType.MAINNET, "", "")
  val addressEncoder = new ErgoAddressEncoder(NetworkType.MAINNET.networkPrefix)


  val FEE = 1e7.toLong

}
