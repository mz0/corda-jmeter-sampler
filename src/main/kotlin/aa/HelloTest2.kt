package aa

import java.io.Serializable
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import org.apache.jmeter.config.Argument
import org.apache.jmeter.config.Arguments
import org.apache.jmeter.protocol.java.sampler.JavaSamplerClient
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext
import org.apache.jmeter.samplers.Interruptible
import org.apache.jmeter.samplers.SampleResult
import org.apache.jmeter.testelement.TestElement

class HelloTest2:  JavaSamplerClient, Serializable, Interruptible {
 // private val nodePrmField = "node host:p2p-port"
 // private lateinit var hostPort: String
    private lateinit var samTag: String
    @Transient lateinit var myThread: Thread
    private var rpcParams: RPCParams? = null

    companion object {
        private const val serialVersionUID = 20191112L
        private data class RPCParams(val address: NetworkHostAndPort, val user: String, val password: String)
        private data class RPCClient(val rpcClient: CordaRPCClient, val rpcConnection: CordaRPCConnection, val ops: CordaRPCOps)
        val host = Argument("host", "192.168.1.191")
        val port = Argument("port", "10001")
        val username = Argument("RPC username", "User1")
        val password = Argument("RPC password", "Passw")
        val rpcArgs = setOf(host, port, username, password)

        val other =  Argument("other party","O=AAVB, L=Bilbao, C=ES")
        val amount = Argument("amount","1,000.99 USD")
        val additionalArgs = setOf(other, amount)
    }

    override fun setupTest(context: JavaSamplerContext) {
        samTag = context.getParameter(TestElement.NAME);
    }

    override fun runTest(context: JavaSamplerContext): SampleResult {
        val results = SampleResult();
        results.setSampleLabel(samTag);
        // prepare test
        rpcParams = RPCParams(NetworkHostAndPort(context.getParameter(host.name), context.getIntParameter(port.name)),
                context.getParameter(username.name),
                context.getParameter(password.name))
        val client = CordaRPCClient(NetworkHostAndPort(context.getParameter(host.name), context.getIntParameter(port.name)))
        val connection = client.start(context.getParameter(username.name), context.getParameter(password.name))
        val cordaRPCOps = connection.proxy

        results.setSamplerData("Test node: " + context.getParameter(host.name));
        try {
            results.sampleStart(); // Record sample start time.
            myThread = Thread.currentThread();
            // do test
            cordaRPCOps.currentNodeTime().toString()
            connection.notifyServerAndClose()
            results.setSuccessful(true);
        } catch (e: InterruptedException) {
            //LOG.warn("Test: interrupted.");
            results.setSuccessful(false);
            results.setResponseMessage(e.toString());
            Thread.currentThread().interrupt();
        } catch (e: Exception) {
            //LOG.error("Test: sampling error", e);
            results.setSuccessful(false);
            results.setResponseMessage(e.toString());
        } finally {
            results.sampleEnd();
        }
        return results
    }

    override fun getDefaultParameters(): Arguments {
        return Arguments().apply {
            for (arg in rpcArgs) {
                addArgument(arg)
            }
            for (arg in additionalArgs) {
                addArgument(arg)
            }
        }
    }

    override fun teardownTest(context: JavaSamplerContext?) { }

    override fun interrupt(): Boolean {
        val alive = ::myThread.isInitialized
        if (alive) myThread.interrupt()
        return alive
    }
}
