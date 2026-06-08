package com.symphonix.enrollmate.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
import io.github.jan.supabase.postgrest.postgrest
import services.DatabaseService
import models.UserModel
import java.time.Instant

@Composable
fun SignUpScreen(navController: NavController, viewModel: AppViewModel) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var passwordVisible by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSuccessDialog by remember { mutableStateOf(false) }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { /* Force user to click the button */ },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text("Verification Sent!", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            },
            text = {
                Text("Your account has been created. A verification link has been sent. Please check your email to activate your account before signing in.", color = Color.DarkGray)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSuccessDialog = false
                        navController.navigate(Screen.SignIn.route) {
                            popUpTo(Screen.SignUp.route) { inclusive = true }
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Go to Sign In", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

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

            TextField(
                value = fullName,
                onValueChange = { fullName = it; errorMessage = null },
                placeholder = { Text("Full Name", color = Color.Gray) },
                enabled = !isLoading,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedContainerColor = Color(0xFFE0E2DB),
                    unfocusedContainerColor = Color(0xFFE0E2DB),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )

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
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )

            TextField(
                value = password,
                onValueChange = { password = it; errorMessage = null },
                placeholder = { Text("Password", color = Color.Gray) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = if (passwordVisible) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }
                },
                enabled = !isLoading,
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedContainerColor = Color(0xFFE0E2DB),
                    unfocusedContainerColor = Color(0xFFE0E2DB),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
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
                        if (fullName.isBlank() || email.isBlank() || password.isBlank()) {
                            errorMessage = "All fields are required."
                            return@Button
                        }

                        // Email format validation
                        val emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[a-zA-Z]{2,}\$".toRegex()
                        if (!email.matches(emailRegex)) {
                            errorMessage = "Please enter a valid email address."
                            return@Button
                        }

                        // Strict Password Validation
                        if (password.length < 8 || !password.any { it.isUpperCase() } || !password.any { it.isDigit() }) {
                            errorMessage = "Password must be at least 8 characters long, contain an uppercase letter and a number."
                            return@Button
                        }

                        isLoading = true
                        viewModel.viewModelScope.launch(Dispatchers.IO) {
                            try {
                                supabase.auth.signUpWith(Email) {
                                    this.email = email
                                    this.password = password
                                }

                                val userId = supabase.auth.currentUserOrNull()?.id
                                if (userId != null) {
                                    val newUser = UserModel(
                                        id = userId.toString(),
                                        fullName = fullName,
                                        email = email,
                                        role = "applicant",
                                        createdAt = Instant.now().toString()
                                    )

                                    try {
                                        supabase.postgrest.from("user").insert(newUser)
                                    } catch (dbError: Exception) {
                                        withContext(Dispatchers.Main) {
                                            errorMessage = "Database Error: ${dbError.message}"
                                            isLoading = false
                                        }
                                        return@launch
                                    }
                                    DatabaseService.upsertUser(newUser)
                                }

                                withContext(Dispatchers.Main) {
                                    showSuccessDialog = true
                                    isLoading = false
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    errorMessage = "Registration failed: ${e.localizedMessage}"
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
                    Text("Sign up", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row {
                Text("Already have an account? ", color = Color.Black)
                Text(
                    text = "Sign in",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { navController.navigate(Screen.SignIn.route) }
                )
            }
        }
    }
}