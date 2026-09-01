package com.budgetide.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.budgetide.app.ui.BudgetideApp
import com.budgetide.app.ui.BudgetideTheme
import com.budgetide.app.ui.OnboardingScreen
import com.budgetide.app.ui.rememberOnboardingCompleted
import com.budgetide.app.viewmodel.MoneyViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate() and before setContentView/setContent.
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            BudgetideTheme {
                val onboardingCompleted by rememberOnboardingCompleted()

                // Keep the splash screen up while we read the onboarding flag from disk,
                // so there's no flash of an empty screen before we know which to show.
                splash.setKeepOnScreenCondition { onboardingCompleted == null }

                when (onboardingCompleted) {
                    null -> { /* still loading, splash screen is covering this */ }
                    false -> OnboardingScreen()
                    true -> {
                        val vm: MoneyViewModel = viewModel()
                        BudgetideApp(vm)
                    }
                }
            }
        }
    }
}
