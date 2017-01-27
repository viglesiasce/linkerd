package grpc.testing

import com.twitter.io.Buf
import com.twitter.util.{Await, Future, Return, Throw, Try}
import io.buoyant.grpc.runtime.{GrpcStatus, Stream, ServerDispatcher}

object TestServiceImpl extends TestService {

  def server = TestService.Server(this)
  def dispatcher = ServerDispatcher(server)

  def emptyCall(empty: Empty): Future[Empty] = Future.value(Empty())

  def unaryCall(req: SimpleRequest): Future[SimpleResponse] =
    req.responsestatus match {
      case Some(EchoStatus(Some(code), msg)) =>
        Future.exception(GrpcStatus(code, msg.getOrElse("")))
      case _ =>
        Future.value(SimpleResponse(None, None, None))
    }

  def cacheableUnaryCall(req: SimpleRequest): Future[SimpleResponse] = {
    Future.value(SimpleResponse(None, None, None))
  }

  /**
   * Echo back each request with a Payload having the requested size
   */
  def fullDuplexCall(
    reqs: Stream[StreamingOutputCallRequest]
  ): Stream[StreamingOutputCallResponse] = {
    val rsps = Stream[StreamingOutputCallResponse]
    def process(): Future[Unit] = {
      println("reading a streaming output call request")
      reqs.recv().transform {
        case Throw(GrpcStatus.Ok(_)) => rsps.close()
        case Throw(e) =>
          val s = e match {
            case s: GrpcStatus => s
            case e => GrpcStatus.Internal(e.getMessage)
          }
          Future.exception(e)

        case Return(Stream.Releasable(req, release)) =>
          println(s"stream output call req $req")
          req.responsestatus match {
            case Some(EchoStatus(Some(code), msg)) =>
              val s = GrpcStatus(code, msg.getOrElse(""))
              rsps.reset(s)
              Future.exception(s)

            case _ =>
              respond(rsps, req.responseparameters)
                .before(release())
                .before(process())
          }
      }
    }
    process()
    rsps
  }

  // TODO: if an interop test can be found that needs this, we will implement it.
  def halfDuplexCall(reqs: Stream[StreamingOutputCallRequest]): Stream[StreamingOutputCallResponse] = ???

  /**
   * Returns the aggregated size of input payloads.
   */
  def streamingInputCall(
    reqs: Stream[StreamingInputCallRequest]
  ): Future[StreamingInputCallResponse] = {
    accumSize(reqs, 0).map { sz => StreamingInputCallResponse(Some(sz)) }
  }

  /**
   * For each ResponseParameter sent, we return a frame in the stream with the requested size.
   */
  def streamingOutputCall(req: StreamingOutputCallRequest): Stream[StreamingOutputCallResponse] = {
    val rsps = Stream[StreamingOutputCallResponse]()
    respond(rsps, req.responseparameters).before(rsps.close())
    rsps
  }

  private[this] def accumSize(
    reqs: Stream[StreamingInputCallRequest],
    processed: Int
  ): Future[Int] =
    reqs.recv().transform {
      case Throw(GrpcStatus.Ok(_)) => Future.value(processed)
      case Throw(e) =>
        val s = e match {
          case s: GrpcStatus => s
          case e => GrpcStatus.Internal(e.getMessage)
        }
        Future.exception(e)

      case Return(Stream.Releasable(req, release)) =>
        val sz = req.payload.flatMap(_.body).map(_.length).getOrElse(0)
        release().before(accumSize(reqs, processed + sz))
    }

  private[this] def respond(
    rsps: Stream.Provider[StreamingOutputCallResponse],
    params: Seq[ResponseParameters]
  ): Future[Unit] = params match {
    case Nil => Future.Unit
    case Seq(param, tail@_*) =>
      val size = param.size.getOrElse(0)
      println(s"streaming frame with ${size} bytes")
      val body = Buf.ByteArray.Owned(Array.fill(size) { 0.toByte })
      val msg = StreamingOutputCallResponse(Some(Payload(None, Some(body))))
      rsps.send(msg).before(respond(rsps, tail))
  }
}
