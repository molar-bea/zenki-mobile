package com.symphonix.enrollmate.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.symphonix.enrollmate.AppViewModel
import com.symphonix.enrollmate.ui.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import services.ApiService

@Composable
fun SignInScreen(navController: NavController, viewModel: AppViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val settings by viewModel.settings.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Sign In",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; errorMessage = null },
            label = { Text("Email") },
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        )

        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            enabled = !isLoading,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )

        if (errorMessage != null) {
            Text(
                text = errorMessage!!,
                color = Color.Red,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(bottom = 16.dp))
        } else {
            Button(
                onClick = {
                    isLoading = true
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val response = ApiService.signIn(email, password)
                            val success = response.optInt("success") == 1
                            if (success) {
                                val fullName = response.optString("fullName")
                                val accessToken = response.optString("accessToken")
                                // Since we don't have user ID in signin response based on the provided python code, 
                                // but we might need it for checklist/appointments. 
                                // The backend code shows it gets full_name and role.
                                // I'll use email as a fallback if needed or assume user ID is available or handled by some other means.
                                // Actually, I'll update AppSettings with the info we have.
                                withContext(Dispatchers.Main) {
                                    viewModel.updateSettings(
                                        settings.copy(
                                            isUserLoggedIn = true,
                                            currentUserFullName = fullName,
                                            currentUserId = email // Using email as ID for now if not provided
                                        )
                                    )
                                    navController.navigate(Screen.Dashboard.route) {
                                        popUpTo(Screen.Landing.route) { inclusive = true }
                                    }
                                }
                            } else {
                                errorMessage = response.optString("message")
                            }
                        } catch (e: Exception) {
                            errorMessage = "Connection error: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Sign In")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row {
            Text("Don't have an account? ")
            Text(
                text = "Sign Up",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { navController.navigate(Screen.SignUp.route) }
            )
        }
    }
}
