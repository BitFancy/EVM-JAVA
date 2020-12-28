package io.horizontalsystems.ethereumkit.core

import io.horizontalsystems.ethereumkit.api.jsonrpc.JsonRpc
import io.horizontalsystems.ethereumkit.api.jsonrpc.models.RpcBlock
import io.horizontalsystems.ethereumkit.api.jsonrpc.models.RpcTransaction
import io.horizontalsystems.ethereumkit.api.jsonrpc.models.RpcTransactionReceipt
import io.horizontalsystems.ethereumkit.models.TransactionLog
import io.horizontalsystems.ethereumkit.models.*
import io.horizontalsystems.ethereumkit.spv.models.AccountState
import io.horizontalsystems.ethereumkit.spv.models.BlockHeader
import io.horizontalsystems.ethereumkit.spv.models.RawTransaction
import io.reactivex.Single
import java.math.BigInteger
import java.util.*


interface IApiStorage {
    fun getLastBlockHeight(): Long?
    fun saveLastBlockHeight(lastBlockHeight: Long)

    fun getBalance(): BigInteger?
    fun saveBalance(balance: BigInteger)
}

interface ISpvStorage {
    fun getLastBlockHeader(): BlockHeader?
    fun saveBlockHeaders(blockHeaders: List<BlockHeader>)
    fun getBlockHeadersReversed(fromBlockHeight: Long, limit: Int): List<BlockHeader>

    fun getAccountState(): AccountState?
    fun saveAccountSate(accountState: AccountState)
}

interface IBlockchain {
    val source: String
    var listener: IBlockchainListener?

    fun start()
    fun refresh()
    fun stop()

    val syncState: EthereumKit.SyncState
    val lastBlockHeight: Long?
    val balance: BigInteger?

    fun send(rawTransaction: RawTransaction): Single<Transaction>
    fun getNonce(): Single<Long>
    fun estimateGas(to: Address?, amount: BigInteger?, gasLimit: Long?, gasPrice: Long?, data: ByteArray?): Single<Long>
    fun getTransactionReceipt(transactionHash: ByteArray): Single<Optional<RpcTransactionReceipt>>
    fun getTransaction(transactionHash: ByteArray): Single<Optional<RpcTransaction>>
    fun getBlock(blockNumber: Long): Single<Optional<RpcBlock>>

    fun getLogs(address: Address?, topics: List<ByteArray?>, fromBlock: Long, toBlock: Long, pullTimestamps: Boolean): Single<List<TransactionLog>>
    fun getStorageAt(contractAddress: Address, position: ByteArray, defaultBlockParameter: DefaultBlockParameter): Single<ByteArray>
    fun call(contractAddress: Address, data: ByteArray, defaultBlockParameter: DefaultBlockParameter): Single<ByteArray>
}

interface IBlockchainListener {
    fun onUpdateLastBlockHeight(lastBlockHeight: Long)
    fun onUpdateBalance(balance: BigInteger)
    fun onUpdateSyncState(syncState: EthereumKit.SyncState)
    fun onUpdateLogsBloomFilter(bloomFilter: BloomFilter)
    fun onUpdateNonce(nonce: Long)

}

interface IRpcApiProvider {
    val source: String

    fun <T> single(rpc: JsonRpc<T>): Single<T>
}

interface ITransactionStorage {
    fun getLastTransactionBlockHeight(): Long?
    fun getLastInternalTransactionBlockHeight(): Long?

    fun saveTransactions(transactions: List<EtherscanTransaction>)
    fun saveInternalTransactions(transactions: List<InternalTransaction>)
    fun getTransactions(fromHash: ByteArray?, limit: Int?): Single<List<TransactionWithInternal>>
}
