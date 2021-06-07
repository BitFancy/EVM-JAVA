package io.horizontalsystems.uniswapkit

import io.horizontalsystems.ethereumkit.contracts.ContractMethod
import io.horizontalsystems.ethereumkit.core.EthereumKit
import io.horizontalsystems.ethereumkit.core.EthereumKit.NetworkType
import io.horizontalsystems.ethereumkit.core.toHexString
import io.horizontalsystems.ethereumkit.models.Address
import io.horizontalsystems.ethereumkit.models.TransactionData
import io.horizontalsystems.uniswapkit.contract.*
import io.horizontalsystems.uniswapkit.models.*
import io.horizontalsystems.uniswapkit.models.Token.Erc20
import io.horizontalsystems.uniswapkit.models.Token.Ether
import io.reactivex.Single
import java.math.BigInteger
import java.util.*
import java.util.logging.Logger

class TradeManager(
        private val evmKit: EthereumKit
) {
    private val address: Address = evmKit.receiveAddress
    private val logger = Logger.getLogger(this.javaClass.simpleName)

    val routerAddress: Address = getRouterAddress(evmKit.networkType)

    fun pair(tokenA: Token, tokenB: Token): Single<Pair> {

        val (token0, token1) = if (tokenA.sortsBefore(tokenB)) Pair(tokenA, tokenB) else Pair(tokenB, tokenA)

        val pairAddress = Pair.address(token0, token1, evmKit.networkType)

        logger.info("pairAddress: ${pairAddress.hex}")

        return evmKit.call(pairAddress, GetReservesMethod().encodedABI())
                .map { data ->
                    logger.info("getReserves data: ${data.toHexString()}")

                    var rawReserve0: BigInteger = BigInteger.ZERO
                    var rawReserve1: BigInteger = BigInteger.ZERO

                    if (data.size == 3 * 32) {
                        rawReserve0 = BigInteger(data.copyOfRange(0, 32))
                        rawReserve1 = BigInteger(data.copyOfRange(32, 64))
                    }

                    val reserve0 = TokenAmount(token0, rawReserve0)
                    val reserve1 = TokenAmount(token1, rawReserve1)

                    logger.info("getReserves reserve0: $reserve0, reserve1: $reserve1")

                    Pair(reserve0, reserve1)
                }
    }

    fun transactionData(tradeData: TradeData): TransactionData {
        return buildSwapData(tradeData).let {
            TransactionData(routerAddress, it.amount, it.input)
        }
    }

    fun estimateSwap(tradeData: TradeData, gasPrice: Long): Single<Long> {
        val swapData = buildSwapData(tradeData)

        return evmKit.estimateGas(
                to = routerAddress,
                value = if (swapData.amount == BigInteger.ZERO) null else swapData.amount,
                gasPrice = gasPrice,
                data = swapData.input
        )
    }

    private class SwapData(val amount: BigInteger, val input: ByteArray)

    private fun buildSwapData(tradeData: TradeData): SwapData {
        val trade = tradeData.trade

        val tokenIn = trade.tokenAmountIn.token
        val tokenOut = trade.tokenAmountOut.token

        val path = trade.route.path.map { it.address }
        val to = tradeData.options.recipient ?: address
        val deadline = (Date().time / 1000 + tradeData.options.ttl).toBigInteger()

        val method = when (trade.type) {
            TradeType.ExactOut -> buildMethodForExactOut(tokenIn, tokenOut, path, to, deadline, tradeData, trade)
            TradeType.ExactIn -> {
                if (tradeData.options.feeOnTransfer) {
                    buildMethodForExactInSupportingFeeOnTransferTokens(tokenIn, tokenOut, path, to, deadline, tradeData, trade)
                } else {
                    buildMethodForExactIn(tokenIn, tokenOut, path, to, deadline, tradeData, trade)
                }
            }
        }

        val amount = if (tokenIn.isEther) {
            when (trade.type) {
                TradeType.ExactIn -> trade.tokenAmountIn.rawAmount
                TradeType.ExactOut -> tradeData.tokenAmountInMax.rawAmount
            }
        } else {
            BigInteger.ZERO
        }

        return SwapData(amount, method.encodedABI())
    }

    private fun buildMethodForExactOut(tokenIn: Token, tokenOut: Token, path: List<Address>, to: Address, deadline: BigInteger, tradeData: TradeData, trade: Trade): ContractMethod {
        val amountInMax = tradeData.tokenAmountInMax.rawAmount
        val amountOut = trade.tokenAmountOut.rawAmount

        return when {
            tokenIn is Ether && tokenOut is Erc20 -> SwapETHForExactTokensMethod(amountOut, path, to, deadline)
            tokenIn is Erc20 && tokenOut is Ether -> SwapTokensForExactETHMethod(amountOut, amountInMax, path, to, deadline)
            tokenIn is Erc20 && tokenOut is Erc20 -> SwapTokensForExactTokensMethod(amountOut, amountInMax, path, to, deadline)
            else -> throw Exception("Invalid tokenIn/Out for swap!")
        }
    }

    private fun buildMethodForExactInSupportingFeeOnTransferTokens(tokenIn: Token, tokenOut: Token, path: List<Address>, to: Address, deadline: BigInteger, tradeData: TradeData, trade: Trade): ContractMethod {
        val amountIn = trade.tokenAmountIn.rawAmount
        val amountOutMin = tradeData.tokenAmountOutMin.rawAmount

        return when {
            tokenIn is Ether && tokenOut is Erc20 -> SwapExactETHForTokensSupportingFeeOnTransferTokensMethod(amountOutMin, path, to, deadline)
            tokenIn is Erc20 && tokenOut is Ether -> SwapExactTokensForETHSupportingFeeOnTransferTokensMethod(amountIn, amountOutMin, path, to, deadline)
            tokenIn is Erc20 && tokenOut is Erc20 -> SwapExactTokensForTokensSupportingFeeOnTransferTokensMethod(amountIn, amountOutMin, path, to, deadline)
            else -> throw Exception("Invalid tokenIn/Out for swap!")
        }
    }

    private fun buildMethodForExactIn(tokenIn: Token, tokenOut: Token, path: List<Address>, to: Address, deadline: BigInteger, tradeData: TradeData, trade: Trade): ContractMethod {
        val amountIn = trade.tokenAmountIn.rawAmount
        val amountOutMin = tradeData.tokenAmountOutMin.rawAmount

        return when {
            tokenIn is Ether && tokenOut is Erc20 -> SwapExactETHForTokensMethod(amountOutMin, path, to, deadline)
            tokenIn is Erc20 && tokenOut is Ether -> SwapExactTokensForETHMethod(amountIn, amountOutMin, path, to, deadline)
            tokenIn is Erc20 && tokenOut is Erc20 -> SwapExactTokensForTokensMethod(amountIn, amountOutMin, path, to, deadline)
            else -> throw Exception("Invalid tokenIn/Out for swap!")
        }
    }

    companion object {

        private fun getRouterAddress(networkType: NetworkType) =
                when (networkType) {
                    NetworkType.EthMainNet,
                    NetworkType.EthRopsten,
                    NetworkType.EthKovan,
                    NetworkType.EthGoerli,
                    NetworkType.EthRinkeby -> Address("0x7a250d5630B4cF539739dF2C5dAcb4c659F2488D")
                    NetworkType.BscMainNet -> Address("0x05fF2B0DB69458A0750badebc4f9e13aDd608C7F")
                }

        fun tradeExactIn(pairs: List<Pair>, tokenAmountIn: TokenAmount, tokenOut: Token, maxHops: Int = 3, currentPairs: List<Pair> = listOf(), originalTokenAmountIn: TokenAmount? = null): List<Trade> {
            //todo validations

            val trades = mutableListOf<Trade>()
            val originalTokenAmountIn = originalTokenAmountIn ?: tokenAmountIn

            for ((index, pair) in pairs.withIndex()) {

                val tokenAmountOut = try {
                    pair.tokenAmountOut(tokenAmountIn)
                } catch (error: Throwable) {
                    continue
                }

                if (tokenAmountOut.token == tokenOut) {
                    val trade = Trade(
                            TradeType.ExactIn,
                            Route(currentPairs + listOf(pair), originalTokenAmountIn.token, tokenOut),
                            originalTokenAmountIn,
                            tokenAmountOut
                    )
                    trades.add(trade)
                } else if (maxHops > 1 && pairs.size > 1) {
                    val pairsExcludingThisPair = pairs.toMutableList().apply { removeAt(index) }
                    val tradesRecursion = tradeExactIn(
                            pairsExcludingThisPair,
                            tokenAmountOut,
                            tokenOut,
                            maxHops - 1,
                            currentPairs + listOf(pair),
                            originalTokenAmountIn
                    )
                    trades.addAll(tradesRecursion)
                }
            }
            return trades
        }

        fun tradeExactOut(pairs: List<Pair>, tokenIn: Token, tokenAmountOut: TokenAmount, maxHops: Int = 3, currentPairs: List<Pair> = listOf(), originalTokenAmountOut: TokenAmount? = null): List<Trade> {
            //todo validations

            val trades = mutableListOf<Trade>()
            val originalTokenAmountOut = originalTokenAmountOut ?: tokenAmountOut

            for ((index, pair) in pairs.withIndex()) {

                val tokenAmountIn = try {
                    pair.tokenAmountIn(tokenAmountOut)
                } catch (error: Throwable) {
                    continue
                }

                if (tokenAmountIn.token == tokenIn) {
                    val trade = Trade(
                            TradeType.ExactOut,
                            Route(listOf(pair) + currentPairs, tokenIn, originalTokenAmountOut.token),
                            tokenAmountIn,
                            originalTokenAmountOut
                    )
                    trades.add(trade)
                } else if (maxHops > 1 && pairs.size > 1) {
                    val pairsExcludingThisPair = pairs.toMutableList().apply { removeAt(index) }
                    val tradesRecursion = tradeExactOut(
                            pairsExcludingThisPair,
                            tokenIn,
                            tokenAmountIn,
                            maxHops - 1,
                            currentPairs + listOf(pair),
                            originalTokenAmountOut
                    )
                    trades.addAll(tradesRecursion)
                }
            }
            return trades
        }

    }

}
