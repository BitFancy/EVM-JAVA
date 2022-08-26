package io.horizontalsystems.nftkit.core

import io.horizontalsystems.nftkit.models.Nft
import io.horizontalsystems.nftkit.models.NftBalance
import io.horizontalsystems.nftkit.models.NftType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class BalanceManager(
    private val balanceSyncManager: BalanceSyncManager,
    private val storage: Storage
) : IBalanceSyncManagerListener {
    private val _nftBalances = MutableStateFlow<List<NftBalance>>(listOf())

    val nftBalancesFlow: Flow<List<NftBalance>>
        get() = _nftBalances.asStateFlow()

    val nftBalances: List<NftBalance>
        get() = _nftBalances.value

    init {
        syncNftBalances()
    }

    private suspend fun handleNftsFromTransactions(type: NftType, nfts: List<Nft>) {
        val existingBalances = storage.nftBalances(type)
        val existingNfts = existingBalances.map { it.nft }
        val newNfts = nfts.filter { !existingNfts.contains(it) }

        storage.setNotSynced(existingNfts)
        storage.saveNftBalances(newNfts.map { NftBalance(it, 0, false) })

        balanceSyncManager.sync()
    }

    suspend fun didSync(nfts: List<Nft>, type: NftType) {
        handleNftsFromTransactions(type, nfts)
    }

    fun syncNftBalances() {
        _nftBalances.update {
            storage.existingNftBalances()
        }
    }

    override fun didFinishSyncBalances() {
        syncNftBalances()
    }
}