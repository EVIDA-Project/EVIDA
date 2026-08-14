package com.example.evida.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.evida.SecurityManager
import com.example.evida.ui.theme.EVIDATheme

class PasscodeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val securityManager = SecurityManager(this)
        val isSetup = intent.getBooleanExtra("isSetup", false)
        val isChangeRequest = intent.getBooleanExtra("isChangeRequest", false)

        setContent {
            EVIDATheme {
                var stage by remember { mutableStateOf(if (isChangeRequest) "VERIFY_CURRENT" else if (isSetup) "SET_NEW" else "UNLOCK") }
                var pin by remember { mutableStateOf("") }

                PasscodeScreen(
                    stage = stage,
                    pinValue = pin,
                    onPinChange = { pin = it },
                ) { enteredPin ->
                    when (stage) {
                        "VERIFY_CURRENT" -> {
                            if (securityManager.verifyPasscode(enteredPin)) {
                                stage = "SET_NEW"
                                pin = "" // CLEAR PIN for new entry
                            } else {
                                Toast.makeText(this, "Incorrect Current PIN", Toast.LENGTH_SHORT).show()
                                pin = ""
                            }
                        }
                        "SET_NEW" -> {
                            securityManager.setPasscode(enteredPin)
                            Toast.makeText(this, "EVIDA PIN Updated", Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK)
                            finish()
                        }
                        "UNLOCK" -> {
                            if (securityManager.verifyPasscode(enteredPin)) {
                                setResult(RESULT_OK)
                                finish()
                            } else {
                                Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show()
                                pin = ""
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PasscodeScreen(
    stage: String, 
    pinValue: String, 
    onPinChange: (String) -> Unit, 
    onPasscodeEntered: (String) -> Unit,
) {
    val title = when(stage) {
        "VERIFY_CURRENT" -> "VERIFY CURRENT PIN"
        "SET_NEW" -> "SET NEW EVIDA PIN"
        else -> "ENTER EVIDA PIN"
    }

    val subtitle = when(stage) {
        "VERIFY_CURRENT" -> "Please confirm your current PIN to continue."
        "SET_NEW" -> "Create a new 6-digit code for your fortress."
        else -> "Access Restricted: Authentication required."
    }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Security, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(
            value = pinValue,
            onValueChange = { if (it.length <= 6) onPinChange(it) },
            label = { Text("6-Digit PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontSize = 24.sp, letterSpacing = 8.sp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { if (pinValue.length == 6) onPasscodeEntered(pinValue) },
            enabled = pinValue.length == 6,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(if (stage == "SET_NEW") "CONFIRM & LOCK" else "CONTINUE")
        }
    }
}
