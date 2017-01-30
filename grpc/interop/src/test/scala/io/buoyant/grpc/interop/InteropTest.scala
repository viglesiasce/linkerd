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

  todo("ping_pong") { await(interop.pingPong()) }
  todo("empty_stream") { await(interop.emptyStream()) }
  todo("timeout_on_sleeping_server") { await(interop.timeoutOnSleepingServer()) }
  todo("cancel_after_begin") { await(interop.cancelAfterBegin()) }
  todo("cancel_after_first_response") { await(interop.cancelAfterFirstResponse()) }
  todo("status_code_and_message") { await(interop.statusCodeAndMessage()) }

  def todo(name: String)(f: => Unit): Unit =
    test(name) {
      assertThrows[Client.UnimplementedException](f)
      cancel("Not implemented")
    }
}
