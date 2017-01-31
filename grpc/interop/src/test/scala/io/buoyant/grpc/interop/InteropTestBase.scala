package io.buoyant.grpc.interop

import com.twitter.util.{Future, Return, Throw, Try}
import grpc.{testing => pb}
import io.buoyant.test.FunSuite

trait InteropTestBase { _: FunSuite =>

  def withClient(f: Client => Future[Unit]): Future[Unit]

  case class Case(name: String, run: Client => Future[Unit])

  def cases: Seq[Case] = Seq(
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

  def todo: Map[String, String] = Map(
    "cancel_after_begin" -> "needs api change?",
    "cancel_after_first_response" -> "needs api change?",
    "status_code_and_message" -> "idk",
    "timeout_on_sleeping_server" -> "can't deadline streams yet..."
  )

  for (Case(name, run) <- cases)
    test(name) {
      todo.get(name) match {
        case None =>
          await(withClient(run))
        case Some(msg) =>
          assertThrows[Throwable](await(withClient(run)))
          cancel(s"TODO: $msg")
      }
    }

}

