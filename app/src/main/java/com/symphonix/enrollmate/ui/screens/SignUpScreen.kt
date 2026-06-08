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
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import services.DatabaseService
import models.UserModel
import java.time.Instant

@Composable
fun SignUpScreen(navController: NavController, viewModel: AppViewModel) {
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
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
                    focusedContainerColor = Color(0xFFE0E2DB),
                    unfocusedContainerColor = Color(0xFFE0E2DB),
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
                        if (fullName.isBlank() || email.isBlank() || password.isBlank()) {
                            errorMessage = "All fields are required."
                            return@Button
                        }
                        isLoading = true
                        viewModel.viewModelScope.launch(Dispatchers.IO) {
                            try {
                                // 1. Tell Supabase to register the new user
                                val authResult = supabase.auth.signUpWith(Email) {
                                    this.email = email
                                    this.password = password
                                }
                                
                                val userId = supabase.auth.currentUserOrNull()?.id
                                if (userId != null) {
                                    val newUser = UserModel(
                                        id = userId.toString(),
                                        fullName = fullName,
                                        email = email,
                                        role = "student", // default role
                                        createdAt = Instant.now().toString()
                                    )

                                    // 2. Save to Supabase DB (Postgrest)
                                    try {
                                        supabase.postgrest.from("user").insert(newUser)
                                    } catch (dbError: Exception) {
                                        // Even if remote insert fails, we might want to continue or show warning
                                        println("Remote DB insert failed: ${dbError.message}")
                                    }

                                    // 3. Save to local DB
                                    DatabaseService.upsertUser(newUser)
                                }

                                withContext(Dispatchers.Main) {
                                    // 4. On success, navigate back to Sign In
                                    navController.navigate(Screen.SignIn.route) {
                                        popUpTo(Screen.SignUp.route) { inclusive = true }
                                    }
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