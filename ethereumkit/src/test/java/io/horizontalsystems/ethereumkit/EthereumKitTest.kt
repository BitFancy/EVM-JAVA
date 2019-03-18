package io.horizontalsystems.ethereumkit

import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.whenever
import io.horizontalsystems.ethereumkit.core.AddressValidator
import io.horizontalsystems.ethereumkit.core.IBlockchain
import io.horizontalsystems.ethereumkit.core.IStorage
import io.horizontalsystems.ethereumkit.models.EthereumTransaction
import io.horizontalsystems.ethereumkit.models.FeePriority
import io.horizontalsystems.ethereumkit.models.State
import io.reactivex.Single
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.web3j.utils.Convert
import java.math.BigDecimal
import java.util.concurrent.Executor

class EthereumKitTest {

    private val blockchain = mock(IBlockchain::class.java)
    private val storage = mock(IStorage::class.java)
    private val addressValidator = mock(AddressValidator::class.java)
    private val state = mock(State::class.java)
    private val listener = mock(EthereumKit.Listener::class.java)
    private lateinit var kit: EthereumKit

    private val ethereumAddress = "ether"

    private val transaction = EthereumTransaction().apply {
        hash = "hash"
        nonce = 123
        input = "input"
        from = "from"
        to = "to"
        value = "3.0"
    }


    @Before
    fun setUp() {
        RxBaseTest.setup()

        whenever(storage.getBalance(any())).thenReturn(null)
        whenever(blockchain.ethereumAddress).thenReturn(ethereumAddress)
        kit = EthereumKit(blockchain, storage, addressValidator, state)
        kit.listener = listener
        kit.listenerExecutor = Executor {
            it.run()
        }
    }

    @Test
    fun testInit_balance() {
        val balance = "123.45"
        val lastBlockHeight = 123

        whenever(storage.getBalance(ethereumAddress)).thenReturn(balance)
        whenever(storage.getLastBlockHeight()).thenReturn(lastBlockHeight)

        kit = EthereumKit(blockchain, storage, addressValidator, state)

        verify(state).balance = balance
        verify(state).lastBlockHeight = lastBlockHeight
    }

    @Test
    fun testStart() {
        kit.start()
        verify(blockchain).start()
    }

    @Test
    fun testStop() {
        kit.stop()
        verify(blockchain).stop()
    }

    @Test
    fun testClear() {
        kit.clear()
        verify(blockchain).clear()
        verify(state).clear()
        verify(storage).clear()
    }

    @Test
    fun testReceiveAddress() {
        val ethereumAddress = "eth_address"
        whenever(blockchain.ethereumAddress).thenReturn(ethereumAddress)
        Assert.assertEquals(ethereumAddress, kit.receiveAddress)
    }

    @Test
    fun testRegister() {
        val address = "address"
        val balance = "123"
        val listenerMock = mock(EthereumKit.Listener::class.java)
        whenever(storage.getBalance(address)).thenReturn(balance)
        whenever(state.hasContract(address)).thenReturn(false)
        kit.register(address, listenerMock)

        verify(state).add(contractAddress = address, listener = listenerMock)
        verify(state).setBalance(balance, address)
        verify(blockchain).register(contractAddress = address)
    }

    @Test
    fun testRegister_alreadyExists() {
        val address = "address"
        val listenerMock = mock(EthereumKit.Listener::class.java)

        whenever(state.hasContract(address)).thenReturn(true)
        kit.register(address, listenerMock)

        verify(state, never()).add(contractAddress = address, listener = listenerMock)
        verify(blockchain, never()).register(contractAddress = address)
    }

    @Test
    fun testUnregister() {
        val address = "address"
        kit.unregister(address)

        verify(state).remove(address)
        verify(blockchain).unregister(address)
    }

    @Test
    fun testValidateAddress() {
        val address = "address"

        kit.validateAddress(address)

        verify(addressValidator).validate(address)
    }

    @Test(expected = AddressValidator.AddressValidationException::class)
    fun testValidateAddress_failed() {
        val address = "address"

        whenever(addressValidator.validate(address)).thenThrow(AddressValidator.AddressValidationException(""))

        kit.validateAddress(address)
    }

    @Test
    fun testFee_defaultPriority() {
        val gasLimit = 21_000
        val gasPrice = 2_000_000_000L

        whenever(blockchain.gasPriceInWei(FeePriority.Medium)).thenReturn(gasPrice)
        whenever(blockchain.gasLimitEthereum).thenReturn(gasLimit)

        val gas = BigDecimal.valueOf(gasPrice)
        val expectedFee = Convert.fromWei(gas.multiply(blockchain.gasLimitEthereum.toBigDecimal()), Convert.Unit.ETHER)

        val fee = kit.fee()

        Assert.assertEquals(expectedFee, fee)
    }

    @Test
    fun testFee_highestGasPrice() {
        val gasLimit = 21_000
        val highestFee = 9_000_000_000
        val highestGasPrice = FeePriority.Highest

        whenever(blockchain.gasLimitEthereum).thenReturn(gasLimit)
        whenever(blockchain.gasPriceInWei(highestGasPrice)).thenReturn(highestFee)

        val gas = BigDecimal.valueOf(highestFee)
        val expectedFee = Convert.fromWei(gas.multiply(blockchain.gasLimitEthereum.toBigDecimal()), Convert.Unit.ETHER)

        val fee = kit.fee(highestGasPrice)

        Assert.assertEquals(expectedFee, fee)
    }

    @Test
    fun testFee_customGasPrice() {
        val gasLimit = 21_000
        val gasPrice = 21_000_000_000
        val customGasPrice = FeePriority.Custom(gasPrice)

        whenever(blockchain.gasLimitEthereum).thenReturn(gasLimit)
        whenever(blockchain.gasPriceInWei(customGasPrice)).thenReturn(gasPrice)

        val gas = BigDecimal.valueOf(gasPrice)
        val expectedFee = Convert.fromWei(gas.multiply(blockchain.gasLimitEthereum.toBigDecimal()), Convert.Unit.ETHER)

        val fee = kit.fee(customGasPrice)

        Assert.assertEquals(expectedFee, fee)
    }

    @Test
    fun testTransactions() {
        val fromHash = "hash"
        val limit = 5
        val expectedResult = Single.just(listOf<EthereumTransaction>())

        whenever(storage.getTransactions(fromHash, limit, null)).thenReturn(expectedResult)

        val result = kit.transactions(fromHash, limit)

        Assert.assertEquals(expectedResult, result)
    }

    @Test
    fun testSend_gasPriceNull() {
        val amount = "23.4"
        val feePriority = FeePriority.Medium
        val toAddress = "address"

        val expectedResult = Single.just(transaction)

        whenever(blockchain.send(toAddress, amount, feePriority)).thenReturn(expectedResult)

        val result = kit.send(toAddress, amount, feePriority)

        Assert.assertEquals(expectedResult, result)
    }

    @Test
    fun testSend_withLowGasPrice() {
        val amount = "23.4"
        val toAddress = "address"
        val feePriority = FeePriority.Low

        val expectedResult = Single.just(transaction)

        whenever(blockchain.send(toAddress, amount, feePriority)).thenReturn(expectedResult)

        val result = kit.send(toAddress, amount, feePriority)

        Assert.assertEquals(expectedResult, result)
    }

    @Test
    fun testBalance() {
        val balance = "32.3"
        whenever(state.balance).thenReturn(balance)
        val result = kit.balance

        Assert.assertEquals(balance, result)
    }

    @Test
    fun testState_syncing() {
        whenever(blockchain.blockchainSyncState).thenReturn(EthereumKit.SyncState.Syncing)
        val result = kit.syncState

        Assert.assertEquals(EthereumKit.SyncState.Syncing, result)
    }

    //
    //Erc20
    //


    @Test
    fun testErc20Fee() {
        val erc20GasLimit = 100_000
        val gasPrice = 1_230_000_000L
        val feePriority = FeePriority.Medium

        whenever(blockchain.gasPriceInWei(feePriority)).thenReturn(gasPrice)
        whenever(blockchain.gasLimitErc20).thenReturn(erc20GasLimit)

        val gas = BigDecimal.valueOf(gasPrice)
        val expectedFee = Convert.fromWei(gas.multiply(blockchain.gasLimitErc20.toBigDecimal()), Convert.Unit.ETHER)

        val fee = kit.feeERC20(feePriority)

        Assert.assertEquals(expectedFee, fee)
    }

    @Test
    fun testErc20Fee_customGasPrice() {
        val erc20GasLimit = 100_000
        val gasPrice = 1_230_000_000L
        val feePriority = FeePriority.Medium

        whenever(blockchain.gasPriceInWei(feePriority)).thenReturn(gasPrice)
        whenever(blockchain.gasLimitErc20).thenReturn(erc20GasLimit)

        val gas = BigDecimal.valueOf(gasPrice)
        val expectedFee = Convert.fromWei(gas.multiply(blockchain.gasLimitErc20.toBigDecimal()), Convert.Unit.ETHER)

        val fee = kit.feeERC20(feePriority)

        Assert.assertEquals(expectedFee, fee)
    }

    @Test
    fun testErc20Balance() {
        val balance = "23.03"
        val address = "address"
        whenever(state.balance(address)).thenReturn(balance)

        val result = kit.balanceERC20(address)
        Assert.assertEquals(balance, result)
    }

    @Test
    fun testState_null() {
        val address = "address"

        whenever(blockchain.syncState(address)).thenReturn(EthereumKit.SyncState.NotSynced)

        val result = kit.syncStateErc20(address)
        Assert.assertEquals(EthereumKit.SyncState.NotSynced, result)
    }

    @Test
    fun testErc20Transaction() {
        val address = "address"
        val fromHash = "hash"
        val limit = 5
        val expectedResult = Single.just(listOf<EthereumTransaction>())

        whenever(storage.getTransactions(fromHash, limit, address)).thenReturn(expectedResult)

        val result = kit.transactionsERC20(address, fromHash, limit)

        Assert.assertEquals(expectedResult, result)
    }

    @Test
    fun testErc20Send_gasPriceNull() {
        val amount = "23.4"
        val toAddress = "address"
        val contractAddress = "contAddress"
        val feePriority = FeePriority.Medium

        val expectedResult = Single.just(transaction)

        whenever(blockchain.sendErc20(toAddress, contractAddress, amount, feePriority)).thenReturn(expectedResult)

        val result = kit.sendERC20(toAddress, contractAddress, amount, feePriority)

        Assert.assertEquals(expectedResult, result)
    }

    @Test
    fun testErc20Send_withCustomGasPrice() {
        val amount = "23.4"
        val toAddress = "address"
        val contractAddress = "contAddress"
        val feePriority = FeePriority.Medium

        val expectedResult = Single.just(transaction)

        whenever(blockchain.sendErc20(toAddress, contractAddress, amount, feePriority)).thenReturn(expectedResult)

        val result = kit.sendERC20(toAddress, contractAddress, amount, feePriority)

        Assert.assertEquals(expectedResult, result)
    }

    @Test
    fun testOnUpdateLastBlockHeight() {
        val height = 34
        val erc20Listener = mock(EthereumKit.Listener::class.java)

        whenever(state.erc20Listeners).thenReturn(listOf(erc20Listener))
        kit.onUpdateLastBlockHeight(height)

        verify(state).lastBlockHeight = height
        verify(listener).onLastBlockHeightUpdate()
        verify(erc20Listener).onLastBlockHeightUpdate()
    }

    @Test
    fun testOnUpdateState() {
        val syncState = EthereumKit.SyncState.Syncing
        kit.onUpdateState(syncState)

        verify(listener).onSyncStateUpdate()
    }

    @Test
    fun testOnUpdateErc20State() {
        val contractAddress = "address"
        val syncState = EthereumKit.SyncState.Syncing
        kit.onUpdateErc20State(syncState, contractAddress)

        verify(state).listener(contractAddress)?.onSyncStateUpdate()
    }

    @Test
    fun testOnUpdateBalance() {
        val balance = "345"
        kit.onUpdateBalance(balance)
        verify(state).balance = balance
        verify(listener).onBalanceUpdate()
    }

    @Test
    fun testOnUpdateErc20Balance() {
        val contractAddress = "address"
        val balance = "345"

        kit.onUpdateErc20Balance(balance, contractAddress)

        verify(state).setBalance(balance, contractAddress)
        verify(state).listener(contractAddress)?.onBalanceUpdate()
    }

    @Test
    fun testOnUpdateTransactions() {
        val transactions = listOf(EthereumTransaction())

        kit.onUpdateTransactions(transactions)
        verify(listener).onTransactionsUpdate(transactions)
    }

    @Test
    fun testOnUpdateTransactions_empty() {
        val transactions = listOf<EthereumTransaction>()

        kit.onUpdateTransactions(transactions)
        verify(listener, never()).onTransactionsUpdate(transactions)
    }

    @Test
    fun testOnUpdateErc20Transactions() {
        val contractAddress = "address"
        val transactions = listOf(EthereumTransaction())

        kit.onUpdateErc20Transactions(transactions, contractAddress)
        verify(state).listener(contractAddress)?.onTransactionsUpdate(transactions)
    }


}
