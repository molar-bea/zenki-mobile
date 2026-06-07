package com.symphonix.enrollmate.ui

sealed class Screen(val route: String) {
    object Landing : Screen("landing")
    object SignIn : Screen("signin")
    object SignUp : Screen("signup")
    object Dashboard : Screen("dashboard")
    object Checklist : Screen("checklist")
    object Appointments : Screen("appointments")
    object Profile : Screen("profile")
}
