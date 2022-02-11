import org.ergoplatform.ErgoAddressEncoder
import org.ergoplatform.appkit.{Address, BlockchainContext, ConstantsBuilder, ErgoClient, ErgoContract, ErgoToken, NetworkType, RestApiErgoClient, SignedTransaction}
import org.ergoplatform.appkit.config.{ErgoNodeConfig, ErgoToolConfig}
import org.ergoplatform.appkit.impl.ErgoTreeContract

import scala.collection.JavaConverters._

object ContractTesting {
  val ergoClient: ErgoClient = RestApiErgoClient.create("http://188.34.207.91:9053/", NetworkType.MAINNET, "", "")
  val addrEnc = new ErgoAddressEncoder(NetworkType.MAINNET.networkPrefix)


  val FEE = 1e7.toLong


  def main(args: Array[String]): Unit = {
    testSwap()
  }

  def testSwap(): Unit = {
    ergoClient.execute(ctx => {
      // A simple contract to swap wrapped Tether and SigmaUSD with tons of problems
      val ergoScript =
        s"""{
           |  val tetherAm = SELF.tokens(0)._2
           |  val sigusdAm = SELF.tokens(1)._2
           |
           |  val newTetherAm = OUTPUTS(0).tokens(0)._2
           |  val newSigusdAm = OUTPUTS(0).tokens(1)._2
           |
           |  val rightContract = SELF.propositionBytes == OUTPUTS(0).propositionBytes
           |  val preserveWealth = tetherAm + sigusdAm == newTetherAm + newSigusdAm
           |  val rightTokens = SELF.tokens(0)._1 == OUTPUTS(0).tokens(0)._1 &&
           |                  SELF.tokens(1)._1 == OUTPUTS(0).tokens(1)._1
           |
           |  sigmaProp(rightContract && preserveWealth && rightTokens)
           |
           |}""".stripMargin

      val swapContract = ctx.compileContract(
        ConstantsBuilder.create()
          .build(),
        ergoScript)
      println(s"The contract's address is: ${addrEnc.fromProposition(swapContract.getErgoTree).get.toString}")

      swapRightAmountOfTokens(ctx, swapContract)
      stealingErgs(ctx, swapContract)
    })
  }

  def stealingErgs(ctx: BlockchainContext, swapContract: ErgoContract): Unit = {
    // dummy prover with dummy secret
    val prover = ctx.newProverBuilder()
      .withDLogSecret(BigInt.apply(0).bigInteger)
      .build()


    val tb = ctx.newTxBuilder()
    val tether = new ErgoToken("f5cc03963b64d3542b8cea49d5436666a97f6a2d098b7d3b2220e824b5a91819", 10000)
    val sigusd = new ErgoToken("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 20000)
    val swapBox = tb.outBoxBuilder() // we build a dummy box and convert it to input so that we can use it in the input of some transactions
      .contract(swapContract)
      .value(2e9.toLong)
      .tokens(tether, sigusd)
      .build()
      .convertToInputWith("ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d", 0)


    val newTether = new ErgoToken("f5cc03963b64d3542b8cea49d5436666a97f6a2d098b7d3b2220e824b5a91819", 10000)
    val newSigusd = new ErgoToken("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 20000)

    // We build a dummy box and convert it to input so that we can use it in the input of some transactions
    val newSwapBox = tb.outBoxBuilder()
      .contract(swapContract)
      .value(1e7.toLong)
      .tokens(newTether, newSigusd)
      .build()

    // Dummy user box with some dummy funds. The user wants to give Tether and get the equivalent amount of SigmaUSD
    val userInBox = tb.outBoxBuilder()
      .contract(new ErgoTreeContract(Address.create("4MQyML64GnzMxZgm").getErgoAddress.script))
      .value(100e9.toLong)
      .build()
      .convertToInputWith("ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d", 0)

    // User's output box where he has collected the equivalent amount of SigUSD
    val userOutBox = tb.outBoxBuilder()
      .contract(new ErgoTreeContract(Address.create("4MQyML64GnzMxZgm").getErgoAddress.script))
      .value(100e9.toLong + 2e9.toLong - 1e7.toLong - FEE)
      .build()

    val tx = tb.boxesToSpend(Seq(swapBox, userInBox).asJava)
      .fee(FEE)
      .outputs(newSwapBox, userOutBox)
      .sendChangeTo(Address.create("4MQyML64GnzMxZgm").getErgoAddress)
      .build()
    var signed: SignedTransaction = null
    try {
      signed = prover.sign(tx)
    } catch {
      case e: Exception => {
        print("User could not steal any ERGs")
        return
      }
    }

    println(s"signed tx: ${signed.toJson(false)}")
    throw new Error("User stole our ERGs!")
  }

  def swapRightAmountOfTokens(ctx: BlockchainContext, swapContract: ErgoContract): Unit = {
    // dummy prover with dummy secret
    val prover = ctx.newProverBuilder()
      .withDLogSecret(BigInt.apply(0).bigInteger)
      .build()


    val tb = ctx.newTxBuilder()
    val tether = new ErgoToken("f5cc03963b64d3542b8cea49d5436666a97f6a2d098b7d3b2220e824b5a91819", 10000)
    val sigusd = new ErgoToken("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 20000)
    val swapBox = tb.outBoxBuilder() // we build a dummy box and convert it to input so that we can use it in the input of some transactions
      .contract(swapContract)
      .value(2e9.toLong)
      .tokens(tether, sigusd)
      .build()
      .convertToInputWith("ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d", 0)


    val newTether = new ErgoToken("f5cc03963b64d3542b8cea49d5436666a97f6a2d098b7d3b2220e824b5a91819", 11000)
    val newSigusd = new ErgoToken("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 19000)

    // We build a dummy box and convert it to input so that we can use it in the input of some transactions
    val newSwapBox = tb.outBoxBuilder()
      .contract(swapContract)
      .value(2e9.toLong)
      .tokens(newTether, newSigusd)
      .build()

    // Dummy user box with some dummy funds. The user wants to give Tether and get the equivalent amount of SigmaUSD
    val userInBox = tb.outBoxBuilder()
      .contract(new ErgoTreeContract(Address.create("4MQyML64GnzMxZgm").getErgoAddress.script))
      .value(100e9.toLong)
      .tokens(new ErgoToken("f5cc03963b64d3542b8cea49d5436666a97f6a2d098b7d3b2220e824b5a91819", 1000))
      .build()
      .convertToInputWith("ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d", 0)

    // User's output box where he has collected the equivalent amount of SigUSD
    val userOutBox = tb.outBoxBuilder()
      .contract(new ErgoTreeContract(Address.create("4MQyML64GnzMxZgm").getErgoAddress.script))
      .value(100e9.toLong - FEE)
      .tokens(new ErgoToken("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 1000))
      .build()

    val tx = tb.boxesToSpend(Seq(swapBox, userInBox).asJava)
      .fee(FEE)
      .outputs(newSwapBox, userOutBox)
      .sendChangeTo(Address.create("4MQyML64GnzMxZgm").getErgoAddress)
      .build()
    val signed = prover.sign(tx)
    println(s"signed tx: ${signed.toJson(false)}")
    throw new Error("User stole our ERGs!")
  }

}
