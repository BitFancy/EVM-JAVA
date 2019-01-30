package io.horizontalsystems.ethereumkit.models

import io.realm.RealmObject
import io.realm.annotations.PrimaryKey

open class Balance : RealmObject {

    @PrimaryKey
    var address = ""

    var balance: Double = 0.0
    var decimal: Int = 0

    constructor()

    constructor(address: String, balance: Double, decimal: Int) {
        this.address = address
        this.balance = balance
        this.decimal = decimal
    }
}
