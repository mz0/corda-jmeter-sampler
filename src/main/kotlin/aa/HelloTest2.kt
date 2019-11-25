package aa

import java.io.Serializable
import net.corda.client.rpc.CordaRPCClient
import net.corda.core.utilities.NetworkHostAndPort
import org.apache.jmeter.config.Argument
import org.apache.jmeter.config.Arguments
import org.apache.jmeter.protocol.java.sampler.JavaSamplerClient
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext
import org.apache.jmeter.samplers.Interruptible
import org.apache.jmeter.samplers.SampleResult
import org.apache.jmeter.testelement.TestElement
import org.slf4j.LoggerFactory

class HelloTest2:  JavaSamplerClient, Serializable, Interruptible {
    private lateinit var samTag: String
    @Transient lateinit var myThread: Thread

    companion object {
        private const val serialVersionUID = 20191112L
        private val LOG = LoggerFactory.getLogger("AA")
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
        val node = "Corda node ${context.getParameter(host.name)}:${context.getIntParameter(port.name)}"
        LOG.info("$node test setup start")
        // prepare test
        val client = CordaRPCClient(NetworkHostAndPort(context.getParameter(host.name), context.getIntParameter(port.name)))
        LOG.info("$node CordaRPCClient created")
        val connection = client.start(context.getParameter(username.name), context.getParameter(password.name))
        LOG.info("$node CordaRPCClient connectton started")
        val rpcOps = connection.proxy
        LOG.info("$node test setup done")
        results.setSamplerData("Test node: " + context.getParameter(host.name));
        try {
            results.sampleStart(); // Record sample start time.
            myThread = Thread.currentThread();
            // do test
            LOG.info("$node time was: ${rpcOps.currentNodeTime()}")
            connection.notifyServerAndClose()
            LOG.info("$node CordaRPCClient connectton closed.")
            results.setSuccessful(true);
        } catch (e: InterruptedException) {
            LOG.warn("$node test interrupted.");
            results.setSuccessful(false);
            results.setResponseMessage(e.toString());
            Thread.currentThread().interrupt();
        } catch (e: Exception) {
            LOG.error("$node sampling error", e);
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
