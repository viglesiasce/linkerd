package io.buoyant.grpc.interop

import com.twitter.util.{Future, Return, Throw, Try}
import grpc.{testing => pb}
import io.buoyant.test.FunSuite

trait InteropTestBase { _: FunSuite =>

  def withClient(f: Client => Future[Unit]): Future[Unit]

  case class Case(name: String, run: Client => Future[Unit])

  val cases: Set[Case] = Set(
    Case("empty_unary", _.emptyUnary()),
    Case("large_unary", _.largeUnary(Client.DefaultLargeReqSize, Client.DefaultLargeRspSize)),
    Case("client_streaming", _.clientStreaming(Client.DefaultReqSizes)),
    Case("server_streaming", _.serverStreaming(Client.DefaultRspSizes)),
    Case("ping_pong", _.pingPong(Client.DefaultReqSizes.zip(Client.DefaultRspSizes))),
    Case("empty_stream", _.emptyStream()),
    Case("cancel_after_begin", _.cancelAfterBegin()),
    Case("cancel_after_first_response", _.cancelAfterFirstResponse()),
    Case("status_code_and_message", _.statusCodeAndMessage()),
    Case("timeout_on_sleeping_server", _.timeoutOnSleepingServer())
  )

  def todo: Set[String] = Set(
    "cancel_after_begin",
    "cancel_after_first_response",
    "status_code_and_message",
    "timeout_on_sleeping_server"
  )

  cases.foreach {
    case Case(name, run) =>
      test(name) {
        if (todo.contains(name)) assertThrows[Exception](await(withClient(run)))
        else await(withClient(run))
      }
  }
}

