package scorex.lagonaki.integration.api

import org.scalatest.{FunSuite, Matchers}
import play.api.libs.json.JsValue
import scorex.app.RunnableApplication
import scorex.block.Block
import scorex.crypto.encode.Base58
import scorex.lagonaki.TransactionTestingCommons
import scorex.transaction.{GenesisTransaction, TransactionsBlockField}
import akka.http.scaladsl.model.headers._

class TransactionsAPISpecification extends FunSuite with Matchers with TransactionTestingCommons {


  override def beforeAll(): Unit = {
    super.beforeAll()
    stopGeneration(applications)
    if (application.wallet.privateKeyAccounts().size < 10) application.wallet.generateNewAccounts(10)
  }

  private def addresses = applicationNonEmptyAccounts.map(_.address)

  test("/transactions/unconfirmed API route") {
    (1 to 20) foreach (_ => genValidTransaction())
    val unconfirmed = transactionModule.utxStorage.all()
    unconfirmed.size shouldBe 20
    val tr = GET.request("/transactions/unconfirmed")
    (tr \\ "signature").toList.size shouldBe unconfirmed.size
  }

  test("/transactions/address/{address}/limit/{limit} API route") {
    addresses.foreach { a =>
      val tr = GET.request(s"/transactions/address/$a/limit/2")
      (tr \\ "amount").toList.size should be <= 2
      checkTransactionList(tr)
    }
  }

  test("/transactions/address/{address}/limit/{limit} with invalid limit value") {
    val response = GET.requestRaw("/transactions/address/1/limit/f")
    assert(response.getStatusCode == 404)
  }

  test("OPTION request should returns CORS headers") {
    val response = OPTIONS.requestRaw("/transactions/address/1/limit/1")
    assert(response.getStatusCode == 200)
    assert(response.getHeader(`Access-Control-Allow-Origin`.*.name()).contains(`Access-Control-Allow-Origin`.*.value()))
    assert(response.getHeader(`Access-Control-Allow-Credentials`(true).name()).contains(`Access-Control-Allow-Credentials`(true).value()))
    assert(response.getHeader(`Access-Control-Allow-Headers`("Authorization", "Content-Type", "X-Requested-With").name()).contains(`Access-Control-Allow-Headers`("Authorization", "Content-Type", "X-Requested-With").value()))
  }

  test("/transactions/info/{signature} API route") {
    val genesisTx = Block.genesis(RunnableApplication.consensusGenesisBlockData, transactionModule.genesisData).transactionDataField.asInstanceOf[TransactionsBlockField].value.head.asInstanceOf[GenesisTransaction]
    val tr = GET.request(s"/transactions/info/${Base58.encode(genesisTx.signature)}")
    (tr \ "signature").as[String] shouldBe Base58.encode(genesisTx.signature)
    (tr \ "type").as[Int] shouldBe 1
    (tr \ "fee").as[Int] shouldBe 0
    (tr \ "amount").as[Long] should be > 0L
    (tr \ "height").as[Int] shouldBe 1
    (tr \ "recipient").as[String] shouldBe genesisTx.recipient.address
  }

  def checkTransactionList(tr: JsValue): Unit = {
    (tr \\ "amount").toList.foreach(amount => amount.as[Long] should be > 0L)
    (tr \\ "fee").toList.foreach(amount => amount.as[Long] should be >= 0L)
    (tr \\ "type").toList.foreach(amount => amount.as[Int] should be >= 0)
    (tr \\ "timestamp").toList.foreach(amount => amount.as[Long] should be >= 0L)
    (tr \\ "signature").toList.size should be >= 0
    (tr \\ "sender").toList.size should be >= 0
    (tr \\ "recipient").toList.size should be >= 0
  }


}
