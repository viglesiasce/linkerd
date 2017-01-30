package io.buoyant.grpc.interop

import io.buoyant.test.FunSuite

class InteropTest extends FunSuite {

  val interop = new Client(new Server)

  test("empty_unary") {
    await(interop.emptyUnary())
  }

  test("large_unary") {
    await(interop.largeUnary(Client.DefaultLargeReqSize, Client.DefaultLargeRspSize))
  }

  test("client_streaming") {
    await(interop.clientStreaming(Client.DefaultReqSizes))
  }

  test("server_streaming") {
    await(interop.serverStreaming(Client.DefaultRspSizes))
  }

  test("ping_pong") {
    await(interop.pingPong(Client.DefaultReqSizes.zip(Client.DefaultRspSizes)))
  }

  test("empty_stream") {
    await(interop.emptyStream())
  }

  todo("cancel_after_begin") {
    await(interop.cancelAfterBegin())
  }

  todo("cancel_after_first_response") { await(interop.cancelAfterFirstResponse()) }
  todo("status_code_and_message") { await(interop.statusCodeAndMessage()) }

  todo("timeout_on_sleeping_server") { await(interop.timeoutOnSleepingServer()) }

  def todo(name: String)(f: => Unit): Unit =
    test(name) {
      assertThrows[Exception](f)
      cancel("Not implemented")
    }
}
