package io.horizontalsystems.ethereumkit.core.refactoring

import io.horizontalsystems.ethereumkit.models.NotSyncedTransaction
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.subjects.PublishSubject
import java.util.logging.Logger

class NotSyncedTransactionPool(
        private val storage: IStorage
) {
    private val logger = Logger.getLogger(this.javaClass.simpleName)

    private val notSyncedTransactionsSubject = PublishSubject.create<Unit>()

    val notSyncedTransactionsSignal: Flowable<Unit>
        get() = notSyncedTransactionsSubject.toFlowable(BackpressureStrategy.BUFFER)

    fun add(notSyncedTransactions: List<NotSyncedTransaction>) {
        val syncedTransactionHashes = storage.getTransactionHashes()

        val newTransactions = notSyncedTransactions.filter { notSyncedTransaction ->
            syncedTransactionHashes.none { notSyncedTransaction.hash.contentEquals(it) }
        }
        storage.addNotSyncedTransactions(newTransactions)

        logger.info("---> add notSyncedTransactions: ${newTransactions.size}")
        if (newTransactions.isNotEmpty()) {
            notSyncedTransactionsSubject.onNext(Unit)

            logger.info("---> notSyncedTransactionsSubject.onNext")
        }
    }

    fun remove(notSyncedTransaction: NotSyncedTransaction) {
        storage.remove(notSyncedTransaction)
    }

    fun update(notSyncedTransaction: NotSyncedTransaction) {
        storage.update(notSyncedTransaction)
    }

    fun getNotSyncedTransactions(limit: Int): List<NotSyncedTransaction> {
        return storage.getNotSyncedTransactions(limit)
    }

}
