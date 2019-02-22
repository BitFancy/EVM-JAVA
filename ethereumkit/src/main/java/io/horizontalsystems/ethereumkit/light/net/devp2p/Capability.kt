package io.horizontalsystems.ethereumkit.light.net.devp2p


data class Capability(val name: String, val version: Byte) : Comparable<Capability> {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Capability) return false

        return this.name == other.name && this.version == other.version
    }

    override fun compareTo(other: Capability): Int {
        val cmp = this.name.compareTo(other.name)
        return if (cmp != 0) {
            cmp
        } else {
            java.lang.Byte.valueOf(this.version).compareTo(other.version)
        }
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + version.toInt()
        return result
    }

    override fun toString(): String {
        return "$name:$version"
    }
}