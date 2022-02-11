# Testing Ergo Contracts Off-chain
This repo contains a simple swap example to show how to test Ergo contracts off-chain using [Ergo Appkit](https://github.com/ergoplatform/ergo-appkit).

# The contract
The contract does a simple swap between two tokens. For the sake of this example, let's say one of the tokens is wrapped Tether and the other is SigUSD!
Here is the contract:
```scala
{
  val tetherAm = SELF.tokens(0)._2
  val sigusdAm = SELF.tokens(1)._2

  val newTetherAm = OUTPUTS(0).tokens(0)._2
  val newSigusdAm = OUTPUTS(0).tokens(1)._2

  val rightContract = SELF.propositionBytes == OUTPUTS(0).propositionBytes
  val preserveWealth = tetherAm + sigusdAm == newTetherAm + newSigusdAm
  val rightTokens = SELF.tokens(0)._1 == OUTPUTS(0).tokens(0)._1 &&
                  SELF.tokens(1)._1 == OUTPUTS(0).tokens(1)._1

  sigmaProp(rightContract && preserveWealth && rightTokens)
}
```

The contract allows swaps in both ways as long as:
- The sum of the tokens and type of them remain the same in the first output
- First output's contract is the same as the swap box

We can compile the contract and assemble some transactions either in Mainnet or Testnet to test its functionalities and trying to find bugs in it. However, that is time consiming since it requires you to:
- Create actual boxes
- Create actual transactions
- Wait for the miners to mine your transactions
- If you want to repeat the same thing after a while, reproducibility may be an issue
A better approach is to write some simple code to test the contract off-chain without it ever going on the blockchain!

In this tutorial we will write two methods to test two scenarios.

# First Scenario: Swap
Let's the main scenario which swapping Tether for SigUSD. Let's say the user wants to swap his 100 Tethers for 100 SigUSD. First let's make some dummy boxes:

### Swap Box
```scala
val tb = ctx.newTxBuilder()
    val tether = new ErgoToken("f5cc03963b64d3542b8cea49d5436666a97f6a2d098b7d3b2220e824b5a91819", 10000)
    val sigusd = new ErgoToken("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 20000)
    val swapBox = tb.outBoxBuilder() // we build a dummy box and convert it to input so that we can use it in the input of some transactions
      .contract(swapContract)
      .value(2e9.toLong)
      .tokens(tether, sigusd)
      .build()
      .convertToInputWith("ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d", 0)
```
Here is a break down of the above code:
- Creating a transaction builder object called **tb**
- Creating 10k test Tether and 20k test SigUSD tokens
- Createing a swap box with 2 ERGs and the two created tokens -- notice that we convert this box to input using `convertToInputWith` with a dummy transaction ID.

### Output Swap Box
```scala
    val newTether = new ErgoToken("f5cc03963b64d3542b8cea49d5436666a97f6a2d098b7d3b2220e824b5a91819", 11000)
    val newSigusd = new ErgoToken("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 19000)

    // We build a dummy box and convert it to input so that we can use it in the input of some transactions
    val newSwapBox = tb.outBoxBuilder()
      .contract(swapContract)
      .value(2e9.toLong)
      .tokens(newTether, newSigusd)
      .build(
```
This box is same as the original one for the most part. The difference is the amount of Tether and SigUSD that it contains. Notice that the sum of the tokens has remained the same. This suggests that the user will swap 1k Tether for 1k SigUSD.

### User Funds Box
```scala
    val userInBox = tb.outBoxBuilder()
      .contract(new ErgoTreeContract(Address.create("4MQyML64GnzMxZgm").getErgoAddress.script))
      .value(100e9.toLong)
      .tokens(new ErgoToken("f5cc03963b64d3542b8cea49d5436666a97f6a2d098b7d3b2220e824b5a91819", 1000))
      .build()
      .convertToInputWith("ce552663312afc2379a91f803c93e2b10b424f176fbc930055c10def2fd88a5d", 0)
```
This is the user's box containing his funds. Here he has 1k Tether which he wish to swap for SigUSD. For simplicity the contract of this box is `sigmaProp(true)`.

### User Output Box
```scala
    val userOutBox = tb.outBoxBuilder()
      .contract(new ErgoTreeContract(Address.create("4MQyML64GnzMxZgm").getErgoAddress.script))
      .value(100e9.toLong - FEE)
      .tokens(new ErgoToken("f9e5ce5aa0d95f5d54a7bc89c46730d9662397067250aa18a0039631c0f5b809", 1000))
      .build()
````
This is the user's output box. Notice that there is 1k SigUSD in it which suggests that 1k Tether is swapped with 1k SigUSD.

## Signing the Transaction
``` scala
    val tx = tb.boxesToSpend(Seq(swapBox, userInBox).asJava)
      .fee(FEE)
      .outputs(newSwapBox, userOutBox)
      .sendChangeTo(Address.create("4MQyML64GnzMxZgm").getErgoAddress)
      .build()
    val signed = prover.sign(tx)
```

# Second Scenario: Stealing ERGs
This contract has many problems. The most noticible one is that it is not preserving the ERG amount of the swap box. So the user can steal the swap box's ERGs easily. This scenario is very similar to the previous one for crating boxes and signing the tx. So we don't go through the details. Here is the piece of code that tires to steal the swap box's ERGs:
```scala
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
```
As you can see, the user is trying to steal 2 ERGs from the swap box. If he succeeds an Error will be thrown indicating that the test has failed. If you run the code, you'll see that the user indeed will be able to steal 2 ERGs from the swap box. This suggests that our contract has holes and needs fixes.

# Moral of the Tutorial
We can efficiently and quickly test different parts and scenarios regarding our smart contracts off-chain. The Ergo Appkit provides tons of other functionalities which makes it a perfect choice for such testing.