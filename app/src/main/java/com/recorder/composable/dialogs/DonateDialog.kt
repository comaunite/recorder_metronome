package com.recorder.composable.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mwa.clientktx.clientlib.ActivityResultSender
import com.recorder.view_models.DonationViewModel

private sealed class DonationOption(val label: String, val lamports: Long?) {
    object Sol10 : DonationOption("0.10 SOL", 100_000_000L)
    object Sol05 : DonationOption("0.05 SOL", 50_000_000L)
    object Sol01 : DonationOption("0.01 SOL", 10_000_000L)
    object Custom : DonationOption("Custom", null)
}

private val PRESET_OPTIONS = listOf(
    DonationOption.Sol10,
    DonationOption.Sol05,
    DonationOption.Sol01,
    DonationOption.Custom
)

private fun validateCustomAmount(input: String): Pair<Boolean, String> {
    if (input.isBlank()) return false to ""
    val value = input.toDoubleOrNull() ?: return false to "Enter a valid number"
    return when {
        value < 0.001 -> false to "Minimum is 0.001 SOL"
        value > 10.0 -> false to "Maximum is 10.00 SOL"
        else -> true to ""
    }
}

@Composable
fun DonateDialog(
    viewModel: DonationViewModel,
    activityResultSender: ActivityResultSender,
    onDismiss: () -> Unit
) {
    val donationState by viewModel.donationState.collectAsStateWithLifecycle()

    var selectedOption by remember { mutableStateOf<DonationOption>(DonationOption.Sol10) }
    var customAmountText by remember { mutableStateOf("") }

    val isLoading = donationState is DonationViewModel.DonationState.Loading
    val isTerminal = donationState is DonationViewModel.DonationState.Success
            || donationState is DonationViewModel.DonationState.Error
            || donationState is DonationViewModel.DonationState.NoWallet

    val (customValid, customError) = if (selectedOption is DonationOption.Custom) {
        validateCustomAmount(customAmountText)
    } else {
        true to ""
    }

    val canDonate = !isLoading && !isTerminal && (selectedOption !is DonationOption.Custom || customValid)

    Dialog(
        onDismissRequest = {
            if (!isLoading) {
                viewModel.resetState()
                onDismiss()
            }
        },
        properties = DialogProperties(dismissOnBackPress = !isLoading, dismissOnClickOutside = !isLoading)
    ) {
        Column(
            modifier = Modifier
                .background(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(20.dp)
                .fillMaxWidth(0.92f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Donate SOL",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "I made this app as a little side project to learn about Android development and partake in the community. As well as, the tool that was included in the default applications package was unusable, and I needed a good recorder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Any support is well appreciated!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )


            Spacer(modifier = Modifier.height(16.dp))

            // Amount options
            if (!isTerminal && !isLoading) {
                Column(modifier = Modifier.selectableGroup().fillMaxWidth()) {
                    PRESET_OPTIONS.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = selectedOption == option,
                                    onClick = { selectedOption = option },
                                    role = Role.RadioButton
                                )
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = selectedOption == option,
                                onClick = null
                            )
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }

                // Custom amount text field
                if (selectedOption is DonationOption.Custom) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = customAmountText,
                        onValueChange = { customAmountText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Amount (SOL)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        isError = customAmountText.isNotBlank() && !customValid,
                        supportingText = {
                            if (customAmountText.isNotBlank() && customError.isNotEmpty()) {
                                Text(customError, color = MaterialTheme.colorScheme.error)
                            } else {
                                Text("Between 0.001 and 10.00", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    )
                }
            }

            // Loading indicator
            if (isLoading) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp))
                }
                Text(
                    text = "Waiting for wallet approval…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Success / error messages
            when (val state = donationState) {
                is DonationViewModel.DonationState.Success -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "✓ Donation sent successfully!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF2E7D32),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Thank you for your support!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF2E7D32),
                        textAlign = TextAlign.Center
                    )
                }
                is DonationViewModel.DonationState.Error -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Transaction failed:\n${state.message}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
                is DonationViewModel.DonationState.NoWallet -> {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No compatible Solana wallet found on this device. Please install a Solana wallet app first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TextButton(
                    onClick = {
                        viewModel.resetState()
                        onDismiss()
                    },
                    enabled = !isLoading
                ) {
                    Text(if (isTerminal) "Close" else "Cancel")
                }

                if (!isTerminal) {
                    Button(
                        onClick = {
                            val lamports = if (selectedOption is DonationOption.Custom) {
                                val sol = customAmountText.toDoubleOrNull() ?: return@Button
                                (sol * DonationViewModel.LAMPORTS_PER_SOL).toLong()
                            } else {
                                selectedOption.lamports ?: return@Button
                            }
                            viewModel.donate(activityResultSender, lamports)
                        },
                        enabled = canDonate
                    ) {
                        Text("Donate")
                    }
                }
            }
        }
    }
}

