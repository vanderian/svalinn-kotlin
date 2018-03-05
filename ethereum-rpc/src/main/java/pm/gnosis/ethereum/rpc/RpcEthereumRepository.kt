package pm.gnosis.ethereum.rpc

import io.reactivex.Observable
import pm.gnosis.ethereum.*
import pm.gnosis.ethereum.models.TransactionParameters
import pm.gnosis.ethereum.models.TransactionReceipt
import pm.gnosis.ethereum.rpc.models.*
import pm.gnosis.models.Transaction
import pm.gnosis.models.Wei
import java.math.BigDecimal
import java.math.BigInteger

class RpcEthereumRepository(
    private val ethereumRpcApi: EthereumRpcApi
) : EthereumRepository {

    override fun <R : EthRequest<*>> bulk(requests: List<R>): Observable<List<R>> =
        Observable.fromCallable { requests.associate { it.id to it.toRpcRequest() } }
            .flatMap { rpcRequests ->
                ethereumRpcApi.post(rpcRequests.values.map { it.request() })
                    .map { it.forEach { rpcRequests[it.id]?.parse(it) } }
                    .map { requests }
            }

    override fun <R : EthRequest<*>> request(request: R): Observable<R> =
        Observable.fromCallable { request.toRpcRequest() }
            .flatMap { rpcRequest ->
                ethereumRpcApi.post(rpcRequest.request())
                    .map { rpcRequest.parse(it) }
                    .map { request }
            }

    override fun getBalance(address: BigInteger): Observable<Wei> =
        request(EthBalance(address))
            .map {
                val resp = it.response
                when (resp) {
                    is EthRequest.Response.Failure -> throw IllegalArgumentException(resp.error)
                    is EthRequest.Response.Success -> resp.data
                    null -> throw IllegalStateException()
                }
            }

    override fun sendRawTransaction(signedTransactionData: String): Observable<String> =
        request(EthSendRawTransaction(signedTransactionData))
            .map {
                it.result() ?: throw IllegalStateException()
            }

    override fun getTransactionReceipt(receiptHash: String): Observable<TransactionReceipt> =
        ethereumRpcApi.receipt(
            JsonRpcRequest(
                method = "eth_getTransactionReceipt",
                params = arrayListOf(receiptHash)
            )
        ).map {
            TransactionReceipt(
                it.result.status,
                it.result.transactionHash,
                it.result.contractAddress,
                it.result.logs.map { TransactionReceipt.Event(it.logIndex, it.data, it.topics) }
            )
        }

    override fun getTransactionParameters(
        from: BigInteger,
        to: BigInteger,
        value: Wei?,
        data: String?
    ): Observable<TransactionParameters> {
        val tx = Transaction(address = to, value = value, data = data)
        val estimateRequest = EthEstimateGas(from, tx, 0)
        val gasPriceRequest = EthGasPrice(1)
        val nonceRequest = EthGetTransactionCount(from, 2)
        return bulk(listOf(estimateRequest, gasPriceRequest, nonceRequest)).map {
            val adjustedGas = BigDecimal.valueOf(1.4)
                .multiply(BigDecimal(estimateRequest.result())).setScale(0, BigDecimal.ROUND_UP)
                .unscaledValue()
            TransactionParameters(adjustedGas, gasPriceRequest.result()!!, nonceRequest.result()!!)
        }
    }
}

private fun <T> EthRequest<T>.toRpcRequest() =
    when (this) {
        is EthCall -> RpcCallRequest(this)
        is EthBalance -> RpcBalanceRequest(this)
        is EthEstimateGas -> RpcEstimateGasRequest(this)
        is EthGasPrice -> RpcGasPriceRequest(this)
        is EthGetTransactionCount -> RpcTransactionCountRequest(this)
        is EthSendRawTransaction -> RpcSendRawTransaction(this)
        else -> throw IllegalArgumentException()
    }