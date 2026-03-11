package com.recorder.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mwa.clientktx.clientlib.ActivityResultSender
import com.mwa.clientktx.clientlib.ConnectionIdentity
import com.mwa.clientktx.clientlib.MobileWalletAdapter
import com.mwa.clientktx.clientlib.Solana
import com.mwa.clientktx.clientlib.TransactionResult
import com.solana.programs.SystemProgram
import com.solana.publickey.SolanaPublicKey
import com.solana.transaction.Message
import com.solana.transaction.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import androidx.core.net.toUri

class DonationViewModel : ViewModel() {

    private val mobileWalletAdapter = MobileWalletAdapter(
        connectionIdentity = ConnectionIdentity(
            identityUri = "https://recorder.app".toUri(),
            iconUri = "favicon.ico".toUri(),
            identityName = "Recorder"
        )
    ).also { it.blockchain = Solana.Mainnet }

    sealed class DonationState {
        object Idle : DonationState()
        object Loading : DonationState()
        object Success : DonationState()
        data class Error(val message: String) : DonationState()
        object NoWallet : DonationState()
    }

    private val _donationState = MutableStateFlow<DonationState>(DonationState.Idle)
    val donationState = _donationState.asStateFlow()

    fun donate(sender: ActivityResultSender, lamports: Long) {
        viewModelScope.launch {
            _donationState.value = DonationState.Loading
            try {
                val result = mobileWalletAdapter.transact(sender) { authResult ->
                    val blockhash = fetchLatestBlockhash()
                        ?: throw RuntimeException("Could not fetch a recent blockhash. Check your internet connection.")

                    val fromKey = SolanaPublicKey(authResult.accounts.first().publicKey)
                    val toKey = SolanaPublicKey.from(DONATION_WALLET)

                    val tx = Transaction(
                        Message.Builder()
                            .addInstruction(SystemProgram.transfer(fromKey, toKey, lamports))
                            .setRecentBlockhash(blockhash)
                            .build()
                    )

                    signAndSendTransactions(arrayOf(tx.serialize()))
                }

                _donationState.value = when (result) {
                    is TransactionResult.Success -> DonationState.Success
                    is TransactionResult.Failure -> DonationState.Error(result.message)
                    is TransactionResult.NoWalletFound -> DonationState.NoWallet
                }
            } catch (e: Exception) {
                _donationState.value = DonationState.Error(e.message ?: "An unknown error occurred.")
            }
        }
    }

    fun resetState() {
        _donationState.value = DonationState.Idle
    }

    private suspend fun fetchLatestBlockhash(): String? = withContext(Dispatchers.IO) {
        try {
            val url = URL(SOLANA_RPC_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("Content-Type", "application/json")
            val body = """{"jsonrpc":"2.0","id":1,"method":"getLatestBlockhash","params":[{"commitment":"finalized"}]}"""
            connection.outputStream.use { it.write(body.toByteArray()) }
            val response = connection.inputStream.reader().readText()
            JSONObject(response)
                .getJSONObject("result")
                .getJSONObject("value")
                .getString("blockhash")
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val DONATION_WALLET = "wallet_address"
        const val SOLANA_RPC_URL = "https://api.mainnet-beta.solana.com"
        const val LAMPORTS_PER_SOL = 1_000_000_000L
    }
}


