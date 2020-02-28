package aa

import com.r3.corda.enterprise.perftestcordapp.DOLLARS
import com.r3.corda.enterprise.perftestcordapp.flows.AbstractCashFlow
import com.r3.corda.enterprise.perftestcordapp.flows.CashIssueFlow
import com.r3.corda.enterprise.perftestcordapp.flows.CashPaymentFromKnownStatesFlow
import java.io.Serializable
import net.corda.client.rpc.CordaRPCClient
import net.corda.client.rpc.CordaRPCConnection
import net.corda.core.contracts.Amount
import net.corda.core.contracts.StateRef
import net.corda.core.flows.FlowLogic
import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.utilities.NetworkHostAndPort
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import org.apache.jmeter.config.Argument
import org.apache.jmeter.config.Arguments
import org.apache.jmeter.protocol.java.sampler.JavaSamplerClient
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext
import org.apache.jmeter.samplers.Interruptible
import org.apache.jmeter.samplers.SampleResult
import org.apache.jmeter.testelement.TestElement
import org.slf4j.LoggerFactory
import java.util.*

class HelloTest2:  JavaSamplerClient, Serializable, Interruptible {
    class FlowInvoke<T : FlowLogic<*>>(val flowLogicClass: Class<out T>, val args: Array<Any?>)
    private lateinit var samTag: String
    private lateinit var rpcOps: CordaRPCOps
    private lateinit var node: String
    private lateinit var client: CordaRPCClient
    private lateinit var connection: CordaRPCConnection

    private lateinit var toParty: Party
    private lateinit var notary: Party
    private var states: Int = 1
    private var changes: Int = 1
    private var anonymously: Boolean = true
    private var flowResult: Any? = null
    @Transient lateinit var myThread: Thread

    companion object {
        private const val serialVersionUID = 20191112L
        private val LOG = LoggerFactory.getLogger("nft")
        val host = Argument("host", "192.168.1.191")
        val port = Argument("port", "10001")
        val username = Argument("RPC username", "User1")
        val password = Argument("RPC password", "Passw")
        val rpcArgs = setOf(host, port, username, password)

        val other =  Argument("other party","O=AAVB, L=Bilbao, C=ES")
        val argNotary = Argument("notary", "O=Notary")
        val numStates = Argument("numberOfStatesPerTx", "1")
        val changeStates = Argument("numberOfChangeStatesPerTx", "1")
        val anon = Argument("anonymousIdentities", "false")

        val additionalArgs = setOf(other, argNotary, numStates, changeStates, anon)
    }

    override fun setupTest(context: JavaSamplerContext) {
        samTag = context.getParameter(TestElement.NAME)

        val toName = CordaX500Name.parse(context.getParameter(other.name))
        val notaryName = CordaX500Name.parse(context.getParameter(argNotary.name))
        val reserve: Amount<Currency>

        states = context.getParameter(numStates.name, numStates.value).toInt()
        changes = context.getParameter(changeStates.name, changeStates.value).toInt()
        anonymously = context.getParameter(anon.name, anon.value).toBoolean()

        node = "Corda node ${context.getParameter(host.name)}:${context.getIntParameter(port.name)}"
        LOG.info("$node test setup start")
        client = CordaRPCClient(NetworkHostAndPort(context.getParameter(host.name), context.getIntParameter(port.name)))
        LOG.info("$node CordaRPCClient created")
        connection = client.start(context.getParameter(username.name), context.getParameter(password.name))
        LOG.info("$node CordaRPCConnection started")
        rpcOps = connection.proxy

        LOG.info("$node resolving counterparty")
        toParty = rpcOps.wellKnownPartyFromX500Name(toName) ?: throw IllegalStateException("Cannot resolve $toName")
        LOG.info("$node resolving notary")
        notary = rpcOps.wellKnownPartyFromX500Name(toName) ?: throw IllegalStateException("Cannot resolve $notaryName")
        LOG.info("$node issuing enough dollars for $states states of 1 USD each") // technically $states is enough, anyway make 2x of them.
        reserve = Amount.fromDecimal((states*2).toBigDecimal(), Currency.getInstance("USD"))
        //val flowInvoke = FlowInvoke<CashIssueFlow>(CashIssueFlow::class.java, arrayOf(reserve, OpaqueBytes.of(1), notary))
        flowResult = rpcOps.startFlowDynamic(CashIssueFlow::class.java, reserve, OpaqueBytes.of(1), notary)
                .returnValue.getOrThrow()
        LOG.info("$node test setup done")
    }

    override fun runTest(context: JavaSamplerContext): SampleResult {
        val flowInvoke = mkCPfKSInvoke()
        val results = SampleResult()
        results.samplerData = "Test node: " + context.getParameter(host.name)
        results.sampleStart() // Record sample start time.
        val handle = rpcOps.startFlowDynamic(flowInvoke.flowLogicClass, *(flowInvoke.args))
        results.sampleLabel = samTag ?: flowInvoke.flowLogicClass.simpleName
        try {
            myThread = Thread.currentThread()
            // do test
            LOG.info("$node time was: ${rpcOps.currentNodeTime()}")
            flowResult = handle.returnValue.get()
            results.isSuccessful = true
        } catch (e: InterruptedException) {
            LOG.warn("$node test interrupted.")
            results.isSuccessful = false
            results.responseMessage = e.toString()
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            LOG.error("$node sampling error", e)
            results.isSuccessful = false
            results.responseMessage = e.toString()
        } finally {
            results.sampleEnd()
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

    override fun teardownTest(context: JavaSamplerContext?) {
        try {
            connection.notifyServerAndClose()
            LOG.info("$node CordaRPCClient connectton closed.")
        } catch (e: Exception) {
            LOG.error("Error closing node connection: $e")
        }
    }

    override fun interrupt(): Boolean {
        val alive = ::myThread.isInitialized
        if (alive) myThread.interrupt()
        return alive
    }

    private fun mkCPfKSInvoke(): FlowInvoke<*> {
        var inputStartIndex = 0
        var inputEndIndex = 0

        val txId = (flowResult as AbstractCashFlow.Result).id  // here gets CashIssueFlow id
        // Change is always the latter outputs
        val inputs = (inputStartIndex..inputEndIndex).map { StateRef(txId, it) }.toSet()
        val amount = 1.DOLLARS
        inputStartIndex = states
        inputEndIndex = inputStartIndex + (changes - 1)
        return FlowInvoke<CashPaymentFromKnownStatesFlow>(CashPaymentFromKnownStatesFlow::class.java,
                arrayOf(inputs, states, changes, amount, toParty, anonymously))
    }
}
