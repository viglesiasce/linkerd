package io.buoyant.grpc.interop

import com.twitter.app.App
import com.twitter.finagle.Path
import com.twitter.finagle.buoyant.H2
import com.twitter.io.Buf
import com.twitter.logging.Logging
import com.twitter.util.{Await, Future, Return, Throw, Try}
import grpc.{testing => pb}
import io.buoyant.grpc.runtime.{GrpcStatus, Stream}
import java.net.InetSocketAddress

object Client extends App with Logging {

  val srvDst = flag("srv-dst", Path.read("/$/inet/127.1/60001"), "server location")

  val DefaultReqSizes = Seq(27182, 8, 1828, 45904)
  val reqSizes = flag("req-sizes", DefaultReqSizes, "request sizes")

  val DefaultRspSizes = Seq(31415, 9, 2653, 58979)
  val rspSizes = flag("rsp-sizes", DefaultRspSizes, "response sizes")

  val DefaultLargeReqSize = 271828
  val largeReqSize = flag("large-req", DefaultLargeReqSize, "large request size")

  val DefaultLargeRspSize = 314159
  val largeRspSize = flag("large-rsp", DefaultLargeRspSize, "large response size")

  val testCase = flag("test-case", "large_unary", "test case to be run")

  def main() {
    val h2 = H2.newService(srvDst().show)
    closeOnExit(h2)
    val client = new Client(new pb.TestService.Client(h2))

    val res = testCase() match {
      case "empty_unary" => client.emptyUnary()
      case "large_unary" => client.largeUnary(largeReqSize(), largeRspSize())
      case "client_streaming" => client.clientStreaming(reqSizes())
      case "server_streaming" => client.serverStreaming(rspSizes())
      case "ping_pong" => client.pingPong()
      case "empty_stream" => client.emptyStream()
      case "timeout_on_sleeping_server" => client.timeoutOnSleepingServer()
      case "cancel_after_begin" => client.cancelAfterBegin()
      case "cancel_after_first_response" => client.cancelAfterFirstResponse()
      case "status_code_and_message" => client.statusCodeAndMessage()
      case name => unimplementedTest(name)
    }

    Await.result(res.liftToTry) match {
      case Return(_) => log.info("success")
      case Throw(e) => log.error(e, "failed")
    }
  }

  private def mkPayload(sz: Int): pb.Payload = {
    val body = Buf.ByteArray.Owned(Array.fill(sz) { 0.toByte })
    pb.Payload(body = Some(body))
  }

  private def unimplementedTest(name: String) =
    Future.exception(new UnimplementedException(name))

  class UnimplementedException(name: String) extends Exception(s"test not implemented: '${name}'")
}

class Client(
  svc: pb.TestService
) {
  import Client.{mkPayload, unimplementedTest}

  def emptyUnary(): Future[Unit] =
    svc.emptyCall(pb.Empty()).flatMap {
      case pb.Empty() => Future.Unit
      case rsp => Future.exception(new IllegalArgumentException(s"unexpected response: $rsp"))
    }

  def largeUnary(reqSize: Int, rspSize: Int): Future[Unit] = {
    val req = pb.SimpleRequest(
      responseType = Some(pb.PayloadType.COMPRESSABLE), // cargocult
      responseSize = Some(rspSize),
      payload = Some(mkPayload(reqSize))
    )
    svc.unaryCall(req).flatMap {
      case pb.SimpleResponse(Some(pb.Payload(_, Some(buf))), _, _) =>
        if (buf.length == rspSize) Future.Unit
        else Future.exception(new IllegalArgumentException(s"received ${buf.length}B, expected ${rspSize}B"))

      case rsp => Future.exception(new IllegalArgumentException(s"unexpected response: $rsp"))
    }
  }

  def clientStreaming(reqSizes: Seq[Int]): Future[Unit] = {
    val reqs = Stream[pb.StreamingInputCallRequest]()

    def sendReqs(szs0: Seq[Int]): Future[Unit] = szs0 match {
      case Nil => reqs.close()
      case Seq(sz, szs1@_*) =>
        val req = pb.StreamingInputCallRequest(payload = Some(mkPayload(sz)))
        reqs.send(req).before(sendReqs(szs1))
    }

    sendReqs(reqSizes).join(svc.streamingInputCall(reqs)).flatMap {
      case (_, pb.StreamingInputCallResponse(Some(sz))) =>
        val sum = reqSizes.sum
        if (sz == sum) Future.Unit
        else Future.exception(new IllegalArgumentException(s"received ${sz}B, expected ${sum}B"))
      case (_, rsp) =>
        Future.exception(new IllegalArgumentException(s"unexpected response: $rsp"))
    }
  }

  def serverStreaming(rspSizes0: Seq[Int]): Future[Unit] = {
    val req = pb.StreamingOutputCallRequest(
      responseParameters = rspSizes0.map { sz => pb.ResponseParameters(size = Some(sz)) }
    )
    val rsps = svc.streamingOutputCall(req)

    def read(rspSizes: Seq[Int]): Future[Unit] = rspSizes match {
      case Nil => Future.Unit
      case Seq(expected, rest@_*) =>
        rsps.recv().transform {
          case Throw(GrpcStatus.Ok(_)) => Future.Unit
          case Throw(e) => Future.exception(e)
          case Return(Stream.Releasable(rsp, release)) => rsp match {
            case pb.StreamingOutputCallResponse(Some(pb.Payload(_, Some(buf)))) =>
              val sz = buf.length
              release().before {
                if (sz != expected) {
                  Future.exception(new IllegalArgumentException(s"recieved ${sz}B, expected ${expected}B"))
                } else read(rest)
              }

            case rsp =>
              release().before(Future.exception(new IllegalArgumentException(s"invalid response: $rsp")))
          }
        }
    }
    read(rspSizes0)
  }

  def pingPong(): Future[Unit] = unimplementedTest("ping_pong")
  def emptyStream(): Future[Unit] = unimplementedTest("empty_stream")
  def timeoutOnSleepingServer(): Future[Unit] = unimplementedTest("timeout_on_sleeping_server")
  def cancelAfterBegin(): Future[Unit] = unimplementedTest("cancel_after_begin")
  def cancelAfterFirstResponse(): Future[Unit] = unimplementedTest("cancel_after_first_response")
  def statusCodeAndMessage(): Future[Unit] = unimplementedTest("status_code_and_message")
}