package io.buoyant.grpc.interop

import com.twitter.finagle.buoyant.H2
import com.twitter.util.Future
import grpc.{testing => pb}
import io.buoyant.test.FunSuite
import java.net.InetSocketAddress

class NetworkedInteropTest extends FunSuite with InteropTestBase {

  val bugUrl = "https://github.com/linkerd/linkerd/issues/1013"
  override def todo = super.todo ++ Map(
    "empty_stream" -> "there's seems to be a race in client stream teardown",
    "status_code_and_message" -> "there's seems to be a race in client stream teardown",
    "large_unary" -> bugUrl,
    "client_streaming" -> bugUrl,
    "ping_pong" -> bugUrl
  )

  override def withClient(run: Client => Future[Unit]): Future[Unit] = {
    val s = H2.serve("127.1:*", (new Server).dispatcher)
    val c = {
      val addr = s.boundAddress.asInstanceOf[InetSocketAddress]
      val dst = s"/$$/inet/127.1/${addr.getPort}"
      H2.newService(dst)
    }
    val client = new Client(new pb.TestService.Client(c))
    run(client).transform { ret =>
      c.close().transform(_ => s.close())
        .transform(_ => Future.const(ret))
    }
  }
}
