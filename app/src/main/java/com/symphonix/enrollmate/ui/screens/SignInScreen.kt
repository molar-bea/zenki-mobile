package com.symphonix.enrollmate.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.symphonix.enrollmate.AppViewModel
import com.symphonix.enrollmate.ui.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import services.supabase
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email

@Composable
fun SignInScreen(navController: NavController, viewModel: AppViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val settings by viewModel.settings.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Enroll with ease.",
                fontSize = 18.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Soft-filled minimalist input fields matching Figma
            TextField(
                value = email,
                onValueChange = { email = it; errorMessage = null },
                placeholder = { Text("Email", color = Color.Gray) },
                enabled = !isLoading,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedContainerColor = Color(0xFFE0E2DB),
                    unfocusedContainerColor = Color(0xFFE0E2DB),
                    disabledContainerColor = Color(0xFFF0F1EE),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            TextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                placeholder = { Text("Password", color = Color.Gray) },
                visualTransformation = PasswordVisualTransformation(),
                enabled = !isLoading,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedContainerColor = Color(0xFFE0E2DB),
                    unfocusedContainerColor = Color(0xFFE0E2DB),
                    disabledContainerColor = Color(0xFFF0F1EE),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            if (isLoading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            } else {
                Button(
                    onClick = {
                        if (email.isBlank() || password.isBlank()) {
                            errorMessage = "Please fill out all fields."
                            return@Button
                        }
                        isLoading = true
                        viewModel.viewModelScope.launch(Dispatchers.IO) {
                            try {
                                // 1. Tell Supabase to sign the user in
                                supabase.auth.signInWith(Email) {
                                    this.email = email // refers to your state variable
                                    this.password = password
                                }

                                withContext(Dispatchers.Main) {
                                    // 2. Update local app settings on success
                                    viewModel.updateSettings(
                                        settings.copy(
                                            isUserLoggedIn = true,
                                            currentUserId = email
                                        )
                                    )
                                    // 3. Navigate to Dashboard
                                    navController.navigate(Screen.Dashboard.route) {
                                        popUpTo(Screen.Landing.route) { inclusive = true }
                                    }
                                }
                            } catch (e: Exception) {
                                // 4. Catch invalid passwords or network issues
                                withContext(Dispatchers.Main) {
                                    errorMessage = e.localizedMessage ?: "Invalid credentials"
                                    isLoading = false
                                }
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .wrapContentWidth()
                        .height(45.dp)
                        .padding(horizontal = 32.dp)
                ) {
                    Text("Sign in", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row {
                Text("Don't have an account? ", color = Color.Black)
                Text(
                    text = "Sign up",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { navController.navigate(Screen.SignUp.route) }
                )
            }
        }
    }
}