package grpc.testing

import com.twitter.finagle.buoyant.H2
import com.twitter.server.TwitterServer
import com.twitter.util.Await
import java.net.InetSocketAddress

/**
 * Runs a TestService for interop testing.
 *
 * The interop tests are described here:
 *   https://github.com/grpc/grpc/blob/master/doc/interop-test-descriptions.md
 */
object ServerMain extends TwitterServer {

  val addr = flag("addr", new InetSocketAddress(60001), "server address")

  def main() {
    val srvstr = {
      val isa = addr()
      val ip = if (isa.getAddress.isAnyLocalAddress) "" else isa.getHostString
      val port = addr().getPort
      s"${ip}:${port}"
    }

    val server = H2.serve(srvstr, TestServiceImpl.dispatcher)
    closeOnExit(server)
    val _ = Await.ready(server)
  }
}
