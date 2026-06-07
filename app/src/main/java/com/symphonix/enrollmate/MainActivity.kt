package com.symphonix.enrollmate

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.symphonix.enrollmate.ui.Screen
import com.symphonix.enrollmate.ui.theme.EnrollMateTheme
import com.symphonix.enrollmate.ui.components.BottomNavigationBar
import com.symphonix.enrollmate.ui.screens.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize DatabaseService with app-specific path
        services.DatabaseService.init(filesDir.absolutePath + "/zenkidb.sqlite")

        enableEdgeToEdge()
        setContent {
            val appViewModel: AppViewModel = viewModel()
            val settings by appViewModel.settings.collectAsState()

            // Handle "Keep screen on"
            LaunchedEffect(settings.keepScreenOn) {
                if (settings.keepScreenOn) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            EnrollMateTheme(useLargeTexts = settings.useLargeTexts) {
                EnrollMateApp(appViewModel)
            }
        }
    }
}

@Composable
fun EnrollMateApp(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val settings by viewModel.settings.collectAsState()

    val showBottomBar = currentRoute in listOf(
        Screen.Dashboard.route,
        Screen.Appointments.route,
        Screen.Checklist.route,
        Screen.Profile.route
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                BottomNavigationBar(navController)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (settings.isUserLoggedIn) Screen.Dashboard.route else Screen.Landing.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Landing.route) { LandingScreen(navController) }
            composable(Screen.SignIn.route) { SignInScreen(navController, viewModel) }
            composable(Screen.SignUp.route) { SignUpScreen(navController, viewModel) }
            composable(Screen.Dashboard.route) { DashboardScreen(viewModel) }
            composable(Screen.Checklist.route) { ChecklistScreen(viewModel) }
            composable(Screen.Appointments.route) { AppointmentsScreen(viewModel) }
            composable(Screen.Profile.route) { ProfileScreen(navController, viewModel) }
        }
    }
}
