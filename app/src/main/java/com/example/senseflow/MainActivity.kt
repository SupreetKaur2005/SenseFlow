package com.example.senseflow

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.senseflow.ui.theme.SenseFlowTheme

// Define AccountType and AccountLastOperationState enums
enum class AccountType {
    CheckingAccount,
    CreditAccount
}

enum class AccountLastOperationState {
    None,
    WithdrawalSuccessful,
    DepositSuccessful,
    DepositFailureAlreadyPaidOff,
    DepositFailureGreaterAmount,
    DepositSuccessfulPaidOff
}

// Define abstract BankAccount class
abstract class BankAccount(val accountType: AccountType) {
    protected var accountBalance: Long by mutableStateOf(0L)
    private var lastOperationState: AccountLastOperationState by mutableStateOf(AccountLastOperationState.None)

    fun accountBalance(): Long {
        return accountBalance
    }

    fun lastOperationState(): AccountLastOperationState {
        return lastOperationState
    }

    protected fun lastOperationState(state: AccountLastOperationState) {
        lastOperationState = state
    }

    abstract fun withdraw(amount: Long): Long
    abstract fun deposit(amount: Long): Long
}

// Define CheckingAccount class
class CheckingAccount : BankAccount(accountType = AccountType.CheckingAccount) {
    override fun withdraw(amount: Long): Long {
        accountBalance -= amount
        lastOperationState(AccountLastOperationState.WithdrawalSuccessful)
        return amount
    }

    override fun deposit(amount: Long): Long {
        accountBalance += amount
        lastOperationState(AccountLastOperationState.DepositSuccessful)
        return amount
    }
}

// Define CreditAccount class
class CreditAccount : BankAccount(accountType = AccountType.CreditAccount) {
    override fun withdraw(amount: Long): Long {
        accountBalance -= amount
        lastOperationState(AccountLastOperationState.WithdrawalSuccessful)
        return amount
    }

    override fun deposit(amount: Long): Long {
        return if (accountBalance == 0L) {
            lastOperationState(AccountLastOperationState.DepositFailureAlreadyPaidOff)
            0L
        } else if (accountBalance + amount > 0L) {
            lastOperationState(AccountLastOperationState.DepositFailureGreaterAmount)
            0L
        } else {
            if (accountBalance + amount == 0L) {
                lastOperationState(AccountLastOperationState.DepositSuccessfulPaidOff)
            } else {
                lastOperationState(AccountLastOperationState.DepositSuccessful)
            }
            accountBalance += amount
            amount
        }
    }
}

class MainActivity : ComponentActivity() {
    private val checkingAccount = CheckingAccount() // Move CheckingAccount instance to MainActivity

    private val speechRecognizerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.get(0) ?: ""
            handleVoiceCommand(spokenText)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SenseFlowTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BankingAppUI(
                        modifier = Modifier.padding(innerPadding),
                        checkingAccount = checkingAccount,
                        onVoiceInputClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now")
                            }
                            speechRecognizerLauncher.launch(intent)
                        }
                    )
                }
            }
        }
    }

    private fun handleVoiceCommand(command: String) {
        val withdrawRegex = Regex("withdraw (\\d+)")
        val depositRegex = Regex("deposit (\\d+)")

        when {
            withdrawRegex.containsMatchIn(command) -> {
                val amount = withdrawRegex.find(command)?.groupValues?.get(1)?.toLongOrNull()
                if (amount != null && checkingAccount.accountBalance() >= amount) {
                    checkingAccount.withdraw(amount)
                    Toast.makeText(this, "Withdrawal of $$amount successful", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Withdrawal failed: Not enough balance", Toast.LENGTH_SHORT).show()
                }
            }
            depositRegex.containsMatchIn(command) -> {
                val amount = depositRegex.find(command)?.groupValues?.get(1)?.toLongOrNull()
                if (amount != null) {
                    checkingAccount.deposit(amount)
                    Toast.makeText(this, "Deposit of $$amount successful", Toast.LENGTH_SHORT).show()
                }
            }
            else -> {
                Toast.makeText(this, "Command not recognized", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun BankingAppUI(
    modifier: Modifier = Modifier,
    checkingAccount: CheckingAccount, // Pass CheckingAccount as a parameter
    onVoiceInputClick: () -> Unit
) {
    val context = LocalContext.current
    var lastOperation by remember { mutableStateOf("None") }
    var depositAmount by remember { mutableStateOf(TextFieldValue("")) }
    var withdrawAmount by remember { mutableStateOf(TextFieldValue("")) }
    var voiceCommand by remember { mutableStateOf("") }

    fun handleVoiceCommand(command: String) {
        val withdrawRegex = Regex("withdraw (\\d+)")
        val depositRegex = Regex("deposit (\\d+)")

        when {
            withdrawRegex.containsMatchIn(command) -> {
                val amount = withdrawRegex.find(command)?.groupValues?.get(1)?.toLongOrNull()
                if (amount != null && checkingAccount.accountBalance() >= amount) {
                    checkingAccount.withdraw(amount)
                    lastOperation = "Withdrawal of $$amount successful"
                } else {
                    lastOperation = "Withdrawal failed: Not enough balance"
                }
            }
            depositRegex.containsMatchIn(command) -> {
                val amount = depositRegex.find(command)?.groupValues?.get(1)?.toLongOrNull()
                if (amount != null) {
                    checkingAccount.deposit(amount)
                    lastOperation = "Deposit of $$amount successful"
                }
            }
            else -> {
                Toast.makeText(context, "Command not recognized", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(voiceCommand) {
        if (voiceCommand.isNotEmpty()) {
            handleVoiceCommand(voiceCommand)
            voiceCommand = ""
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Balance: $${checkingAccount.accountBalance()}",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = "Current balance is ${checkingAccount.accountBalance()} dollars. This is the total amount of money available in your account."
            }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = lastOperation,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = "Last operation: $lastOperation. This indicates the most recent transaction performed on your account."
            }
        )
        Spacer(modifier = Modifier.height(32.dp))

        BasicTextField(
            value = depositAmount,
            onValueChange = { depositAmount = it },
            modifier = Modifier
                .background(Color.LightGray)
                .padding(8.dp)
                .clearAndSetSemantics {
                    contentDescription = "Enter deposit amount. This field allows you to input the amount of money you wish to deposit into your account."
                }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val amount = depositAmount.text.toLongOrNull() ?: 0L
                checkingAccount.deposit(amount)
                lastOperation = "Deposit Successful"
                depositAmount = TextFieldValue("")
            },
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = "Deposit button. Press this button to deposit the entered amount into your account."
            }
        ) {
            Text(text = "Deposit")
        }

        Spacer(modifier = Modifier.height(32.dp))

        BasicTextField(
            value = withdrawAmount,
            onValueChange = { withdrawAmount = it },
            modifier = Modifier
                .background(Color.LightGray)
                .padding(8.dp)
                .clearAndSetSemantics {
                    contentDescription = "Enter withdraw amount. This field allows you to input the amount of money you wish to withdraw from your account."
                }
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val amount = withdrawAmount.text.toLongOrNull() ?: 0L
                if (checkingAccount.accountBalance() >= amount) {
                    checkingAccount.withdraw(amount)
                    lastOperation = "Withdrawal Successful"
                } else {
                    lastOperation = "Withdrawal Failed: Not Enough Balance"
                }
                withdrawAmount = TextFieldValue("")
            },
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = "Withdraw button. Press this button to withdraw the entered amount from your account."
            }
        ) {
            Text(text = "Withdraw")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Voice Input Button
        Button(
            onClick = {
                onVoiceInputClick()
                // No need to manually set voiceCommand here, it will be set based on the actual voice recognition result
            },
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = "Voice input button. Press this button to start voice recognition for inputting commands or queries."
            }
        ) {
            Text(text = "Voice Input")
        }
    }
}

// Example usage
@Preview(showBackground = true)
@Composable
fun BankingAppUIPreview() {
    SenseFlowTheme {
        val checkingAccount = CheckingAccount() // Ensure to create an instance here for preview
        BankingAppUI(onVoiceInputClick = {}, checkingAccount = checkingAccount)
    }
}
