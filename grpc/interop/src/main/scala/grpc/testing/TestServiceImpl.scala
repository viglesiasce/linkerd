package grpc.testing

import com.twitter.io.Buf
import com.twitter.util.{Await, Future, Return, Throw, Try}
import io.buoyant.grpc.runtime.{GrpcStatus, Stream, ServerDispatcher}

object TestServiceImpl extends TestService {

  def server = TestService.Server(this)
  def dispatcher = ServerDispatcher(server)

  def emptyCall(empty: Empty): Future[Empty] = Future.value(Empty())

  def unaryCall(req: SimpleRequest): Future[SimpleResponse] =
    getStatus(req.responseStatus) match {
      case Some(status) => Future.exception(status)
      case None =>
        // req.responseType match { .. }
        val payload = mkPayload(req.responseSize.getOrElse(0))
        Future.value(SimpleResponse(payload = Some(payload)))
    }

  def cacheableUnaryCall(req: SimpleRequest) = unaryCall(req)

  /**
   * Echo back each request with a Payload having the requested size
   */
  def fullDuplexCall(
    reqs: Stream[StreamingOutputCallRequest]
  ): Stream[StreamingOutputCallResponse] = {
    val rsps = Stream[StreamingOutputCallResponse]

    def process(): Future[Unit] = reqs.recv().transform {
      case Throw(GrpcStatus.Ok(_)) => rsps.close()
      case Throw(s: GrpcStatus) => Future.exception(s)
      case Throw(e) => Future.exception(GrpcStatus.Internal(e.getMessage))

      case Return(Stream.Releasable(req, release)) =>
        getStatus(req.responseStatus) match {
          case Some(status) =>
            rsps.reset(status)
            Future.exception(status)

          case None =>
            streamResponses(rsps, req.responseParameters)
              .before(release())
              .before(process())
        }
    }

    process()
    rsps
  }

  // TODO: if an interop test can be found that needs this, we will
  // implement it.
  def halfDuplexCall(
    reqs: Stream[StreamingOutputCallRequest]
  ): Stream[StreamingOutputCallResponse] = ???

  /**
   * Returns the aggregated size of input payloads.
   */
  def streamingInputCall(
    reqs: Stream[StreamingInputCallRequest]
  ): Future[StreamingInputCallResponse] =
    accumSize(reqs, 0).map { sz => StreamingInputCallResponse(Some(sz)) }

  private[this] def accumSize(
    reqs: Stream[StreamingInputCallRequest],
    processed: Int
  ): Future[Int] = reqs.recv().transform {
    case Throw(GrpcStatus.Ok(_)) =>
      Future.value(processed)

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

  /**
   * For each ResponseParameter sent, we return a frame in the stream with the requested size.
   */
  def streamingOutputCall(req: StreamingOutputCallRequest): Stream[StreamingOutputCallResponse] = {
    val rsps = Stream[StreamingOutputCallResponse]()
    streamResponses(rsps, req.responseParameters).before(rsps.close())
    rsps
  }

  private[this] def streamResponses(
    rsps: Stream.Provider[StreamingOutputCallResponse],
    params: Seq[ResponseParameters]
  ): Future[Unit] = params match {
    case Nil => Future.Unit
    case Seq(param, tail@_*) =>
      val payload = mkPayload(param.size.getOrElse(0))
      val msg = StreamingOutputCallResponse(Some(payload))
      rsps.send(msg).before(streamResponses(rsps, tail))
  }

  private[this] def getStatus(es: Option[EchoStatus]): Option[GrpcStatus] = es match {
    case None => None
    case Some(EchoStatus(Some(code), msg)) => Some(GrpcStatus(code, msg.getOrElse("")))
    case Some(EchoStatus(None, msg)) => Some(GrpcStatus.Unknown(msg.getOrElse("")))
  }

  private[this] def mkPayload(sz: Int): Payload = {
    val body = Buf.ByteArray.Owned(Array.fill(sz) { 0.toByte })
    Payload(body = Some(body))
  }
}
