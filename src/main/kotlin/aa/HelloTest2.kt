package aa

import java.io.Serializable
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerClient
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.Interruptible;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;

class HelloTest2:  JavaSamplerClient, Serializable, Interruptible {
    private val nodePrmField = "node host:p2p-port"
    private lateinit var hostPort: String
    private lateinit var samTag: String
    @Transient lateinit var myThread: Thread
    companion object {
        private const val serialVersionUID = 20191122L
    }

    override fun setupTest(context: JavaSamplerContext) {
        hostPort = context.getParameter(nodePrmField);
        samTag = context.getParameter(TestElement.NAME);
    }

    override fun runTest(context: JavaSamplerContext?): SampleResult {
        val results = SampleResult();
        results.setSampleLabel(samTag);
        // prepare test
        results.setSamplerData("Test node: " + hostPort);
        try {
            results.sampleStart(); // Record sample start time.
            myThread = Thread.currentThread();
            // do test
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
        val params = Arguments()
        params.addArgument(nodePrmField,"localhost:10011","","e.g. 192.168.1.190:10021")
//        params.addArgument("other party","100","","e.g. O=AAVB, L=Bilbao, C=ES")
//        params.addArgument("Cash","100_000_000","","e.g. 10_000 GBP");
        return params
    }

    override fun teardownTest(context: JavaSamplerContext?) { }

    override fun interrupt(): Boolean {
        val alive = ::myThread.isInitialized
        if (alive) myThread.interrupt()
        return alive
    }
}
