package io.horizontalsystems.ethereumkit.core

import android.content.Context
import io.horizontalsystems.ethereumkit.api.ApiBlockchain
import io.horizontalsystems.ethereumkit.api.models.EthereumKitState
import io.horizontalsystems.ethereumkit.api.storage.ApiRoomStorage
import io.horizontalsystems.ethereumkit.models.EthereumLog
import io.horizontalsystems.ethereumkit.models.EthereumTransaction
import io.horizontalsystems.ethereumkit.models.TransactionInfo
import io.horizontalsystems.ethereumkit.spv.core.SpvBlockchain
import io.horizontalsystems.ethereumkit.spv.core.SpvRoomStorage
import io.horizontalsystems.ethereumkit.spv.crypto.CryptoUtils
import io.horizontalsystems.ethereumkit.spv.net.INetwork
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import java.math.BigDecimal
import java.math.BigInteger

class EthereumKit(
        private val blockchain: IBlockchain,
        private val addressValidator: AddressValidator,
        private val transactionBuilder: TransactionBuilder,
        private val state: EthereumKitState = EthereumKitState()) : IBlockchainListener {

    private val lastBlockHeightSubject = PublishSubject.create<Long>()
    private val syncStateSubject = PublishSubject.create<SyncState>()
    private val balanceSubject = PublishSubject.create<BigInteger>()
    private val transactionsSubject = PublishSubject.create<List<TransactionInfo>>()

    private val gasLimit: Long = 21_000

    init {
        state.balance = blockchain.balance
        state.lastBlockHeight = blockchain.lastBlockHeight
    }

    fun start() {
        blockchain.start()
    }

    fun stop() {
        blockchain.stop()
    }

    fun refresh() {
        blockchain.refresh()
    }

    fun clear() {
        blockchain.stop()
        blockchain.clear()
        state.clear()
    }

    val lastBlockHeight: Long?
        get() = state.lastBlockHeight

    val balance: BigInteger?
        get() = state.balance

    val syncState: SyncState
        get() = blockchain.syncState

    val receiveAddress: String
        get() = blockchain.address.toEIP55Address()

    val receiveAddressRaw: ByteArray
        get() = blockchain.address

    fun validateAddress(address: String) {
        addressValidator.validate(address)
    }

    fun fee(gasPrice: Long): BigDecimal {
        return BigDecimal(gasPrice).multiply(gasLimit.toBigDecimal())
    }

    fun transactions(fromHash: String? = null, limit: Int? = null): Single<List<TransactionInfo>> {
        return blockchain.getTransactions(fromHash?.hexStringToByteArray(), limit)
                .map { txs -> txs.map { TransactionInfo(it) } }
    }

    fun send(toAddress: String, value: String, gasPrice: Long): Single<EthereumTransaction> {
        val rawTransaction = transactionBuilder.rawTransaction(gasPrice, gasLimit, toAddress.hexStringToByteArray(), value.toBigInteger())

        return blockchain.send(rawTransaction)
    }

    fun send(toAddress: ByteArray, value: BigInteger, transactionInput: ByteArray, gasPrice: Long, gasLimit: Long = this.gasLimit): Single<TransactionInfo> {
        val rawTransaction = transactionBuilder.rawTransaction(gasPrice, gasLimit, toAddress, value, transactionInput)
        return blockchain.send(rawTransaction)
                .map { TransactionInfo(it) }
    }

    fun getLogs(address: ByteArray?, topics: List<ByteArray?>, fromBlock: Long, toBlock: Long, pullTimestamps: Boolean): Single<List<EthereumLog>> {
        return blockchain.getLogs(address, topics, fromBlock, toBlock, pullTimestamps)
    }

    fun getStorageAt(contractAddress: ByteArray, position: ByteArray, blockNumber: Long): Single<ByteArray> {
        return blockchain.getStorageAt(contractAddress, position, blockNumber)
    }

    fun call(contractAddress: ByteArray, data: ByteArray, blockNumber: Long? = null): Single<ByteArray> {
        return blockchain.call(contractAddress, data, blockNumber)
    }

    fun debugInfo(): String {
        val lines = mutableListOf<String>()
        lines.add("ADDRESS: ${blockchain.address}")
        return lines.joinToString { "\n" }
    }

    val lastBlockHeightFlowable: Flowable<Long>
        get() = lastBlockHeightSubject.toFlowable(BackpressureStrategy.BUFFER)

    val syncStateFlowable: Flowable<SyncState>
        get() = syncStateSubject.toFlowable(BackpressureStrategy.BUFFER)

    val balanceFlowable: Flowable<BigInteger>
        get() = balanceSubject.toFlowable(BackpressureStrategy.BUFFER)

    val transactionsFlowable: Flowable<List<TransactionInfo>>
        get() = transactionsSubject.toFlowable(BackpressureStrategy.BUFFER)

    //
    //IBlockchain
    //

    override fun onUpdateLastBlockHeight(lastBlockHeight: Long) {
        if (state.lastBlockHeight == lastBlockHeight)
            return

        state.lastBlockHeight = lastBlockHeight
        lastBlockHeightSubject.onNext(lastBlockHeight)
    }

    override fun onUpdateSyncState(syncState: SyncState) {
        syncStateSubject.onNext(syncState)
    }

    override fun onUpdateBalance(balance: BigInteger) {
        if (state.balance == balance)
            return

        state.balance = balance
        balanceSubject.onNext(balance)
    }

    override fun onUpdateTransactions(ethereumTransactions: List<EthereumTransaction>) {
        if (ethereumTransactions.isEmpty())
            return

        transactionsSubject.onNext(ethereumTransactions.map { tx -> TransactionInfo(tx) })
    }

    sealed class SyncState {
        object Synced : SyncState()
        object NotSynced : SyncState()
        object Syncing : SyncState()
    }

    companion object {
        fun getInstance(context: Context, privateKey: BigInteger, syncMode: SyncMode, networkType: NetworkType, walletId: String): EthereumKit {
            val blockchain: IBlockchain

            val publicKey = CryptoUtils.ecKeyFromPrivate(privateKey).publicKeyPoint.getEncoded(false).drop(1).toByteArray()
            val address = CryptoUtils.sha3(publicKey).takeLast(20).toByteArray()

            val network = networkType.getNetwork()
            val transactionSigner = TransactionSigner(network, privateKey)
            val transactionBuilder = TransactionBuilder()

            when (syncMode) {
                is SyncMode.ApiSyncMode -> {
                    val storage = ApiRoomStorage("api-$walletId-${networkType.name}", context)
                    blockchain = ApiBlockchain.getInstance(storage, networkType, transactionSigner, transactionBuilder, address, syncMode.infuraKey, syncMode.etherscanKey)
                }
                is SyncMode.SpvSyncMode -> {
                    val nodeKey = CryptoUtils.ecKeyFromPrivate(syncMode.nodePrivateKey)
                    val storage = SpvRoomStorage(context, "spv-$walletId-${networkType.name}")

                    blockchain = SpvBlockchain.getInstance(storage, transactionSigner, transactionBuilder, network, address, nodeKey)
                }
            }

            val addressValidator = AddressValidator()

            val ethereumKit = EthereumKit(blockchain, addressValidator, transactionBuilder)

            blockchain.listener = ethereumKit

            return ethereumKit
        }
    }

    sealed class SyncMode {
        class SpvSyncMode(val nodePrivateKey: BigInteger) : SyncMode()
        class ApiSyncMode(val infuraKey: String, val etherscanKey: String) : SyncMode()
    }

    enum class NetworkType {
        MainNet,
        Ropsten,
        Kovan,
        Rinkeby;

        fun getNetwork(): INetwork {
            return io.horizontalsystems.ethereumkit.spv.net.Ropsten()
        }
    }

}
