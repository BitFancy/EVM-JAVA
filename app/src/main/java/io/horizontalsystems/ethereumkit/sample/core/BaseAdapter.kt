package io.horizontalsystems.ethereumkit.sample.core

import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.reactivex.Single
import io.reactivex.subjects.PublishSubject
import java.math.BigDecimal

open class BaseAdapter(val ethereumKit: EthereumKit, val decimal: Int) {

    val transactionSubject = PublishSubject.create<Unit>()
    val balanceSubject = PublishSubject.create<Unit>()
    val lastBlockHeightSubject = PublishSubject.create<Unit>()
    val syncStateUpdateSubject = PublishSubject.create<Unit>()

    open val syncState: EthereumKit.SyncState = EthereumKit.SyncState.NotSynced

    open val balanceString: String?
        get() {
            return null
        }

    protected open fun sendSingle(address: String, amount: String): Single<Unit> {
        return Single.just(Unit)
    }

    open fun transactionsSingle(hashFrom: String? = null, limit: Int? = null): Single<List<TransactionRecord>> {
        return Single.just(listOf())
    }

    val balance: BigDecimal
        get() {
            balanceString?.toBigDecimalOrNull()?.let {
                val converted = it.movePointLeft(decimal)
                return converted.stripTrailingZeros()
            }

            return BigDecimal.ZERO
        }

    fun sendSingle(address: String, amount: BigDecimal): Single<Unit> {
        val poweredDecimal = amount.scaleByPowerOfTen(decimal)
        val noScaleDecimal = poweredDecimal.setScale(0)

        return sendSingle(address, noScaleDecimal.toPlainString())
    }
}
