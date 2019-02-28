package io.horizontalsystems.ethereumkit.light.net.connection

import io.horizontalsystems.ethereumkit.light.crypto.ICrypto
import io.horizontalsystems.ethereumkit.light.xor
import org.spongycastle.crypto.digests.KeccakDigest

class FrameCodecHelper(val crypto: ICrypto) {

    fun updateMac(mac: KeccakDigest, macKey: ByteArray, data: ByteArray): ByteArray {
        val macDigest = ByteArray(mac.digestSize)
        KeccakDigest(mac).doFinal(macDigest, 0)

        val encryptedMacDigest = crypto.encryptAES(macKey, macDigest)

        mac.update(encryptedMacDigest.xor(data), 0, 16)
        KeccakDigest(mac).doFinal(macDigest, 0)

        return macDigest.copyOfRange(0, 16) //checksum
    }

    fun fromThreeBytes(byteArray: ByteArray): Int {
        var num = byteArray[0].toInt() and 0xFF
        num = (num shl 8) + (byteArray[1].toInt() and 0xFF)
        num = (num shl 8) + (byteArray[2].toInt() and 0xFF)
        return num
    }

    fun toThreeBytes(num: Int): ByteArray {
        val byteArray = ByteArray(3)
        byteArray[0] = (num shr 16).toByte()
        byteArray[1] = (num shr 8).toByte()
        byteArray[2] = num.toByte()
        return byteArray
    }
}