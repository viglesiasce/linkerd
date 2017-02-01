package io.buoyant.grpc.interop

import com.twitter.finagle.buoyant.H2
import com.twitter.util.Future
import grpc.{testing => pb}
import io.buoyant.test.FunSuite
import java.net.InetSocketAddress

class NetworkedInteropTest extends FunSuite with InteropTestBase {

  def bugUrl = "https://github.com/linkerd/linkerd/issues/1013"
  override def todo = super.todo ++ Map(
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
    setLogLevel(com.twitter.logging.Level.ALL)
    run(client).transform { ret =>
      setLogLevel(com.twitter.logging.Level.OFF)
      c.close()
        .transform(_ => s.close())
        .transform(_ => Future.const(ret))
    }
  }
}
