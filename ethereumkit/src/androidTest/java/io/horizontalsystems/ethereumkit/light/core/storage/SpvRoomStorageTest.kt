package io.horizontalsystems.ethereumkit.light.core.storage

import android.arch.persistence.room.Room
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import io.horizontalsystems.ethereumkit.light.net.Ropsten
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpvRoomStorageTest {

    private lateinit var blockHeaderDao: BlockHeaderDao
    private lateinit var db: SpvDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getTargetContext()
        db = Room.inMemoryDatabaseBuilder(context, SpvDatabase::class.java).build()
        blockHeaderDao = db.blockHeaderDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun blockHeaderDao() {
        val blockHeader = Ropsten().checkpointBlock

        blockHeaderDao.insert(blockHeader)

        val headerFromDb = blockHeaderDao.getByHashHex(blockHeader.hashHex)
        Assert.assertNotNull(headerFromDb)
    }

}
