package io.horizontalsystems.ethereumkit.light.net.connection

import org.spongycastle.crypto.digests.KeccakDigest

data class Secrets(var aes: ByteArray,
                   var mac: ByteArray,
                   var token: ByteArray,
                   var egressMac: KeccakDigest,
                   var ingressMac: KeccakDigest)