package com.budgetide.app.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore by preferencesDataStore(name = "budgetide_prefs")
private val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")

/** null while loading from disk, then true/false once known. */
@Composable
fun rememberOnboardingCompleted(): State<Boolean?> {
    val context = LocalContext.current
    return produceState<Boolean?>(initialValue = null) {
        context.dataStore.data.map { it[ONBOARDING_DONE] ?: false }.collect { value = it }
    }
}

private data class OnboardingPage(val icon: ImageVector, val title: String, val description: String)

private val onboardingPages = listOf(
    OnboardingPage(
        Icons.Default.Lock,
        "Your data stays on your phone",
        "Budgetide works fully offline. No servers, no accounts \u2014 no one else ever sees your numbers."
    ),
    OnboardingPage(
        Icons.Default.Insights,
        "See where your money goes",
        "Track income and expenses, spot unnecessary spending, and understand your habits."
    ),
    OnboardingPage(
        Icons.Default.Savings,
        "Plan with confidence",
        "Set savings goals, track EMIs and warranties, and see what a pay cut would really mean."
    )
)

@Composable
fun OnboardingScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })

    fun finish() {
        scope.launch {
            context.dataStore.edit { it[ONBOARDING_DONE] = true }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { finish() }) {
                Text("Skip")
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            val p = onboardingPages[page]
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = p.icon,
                    contentDescription = null,
                    modifier = Modifier.size(96.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = p.title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = p.description,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            onboardingPages.indices.forEach { i ->
                val selected = pagerState.currentPage == i
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(if (selected) 10.dp else 8.dp)
                        .background(
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        )
                )
            }
        }

        Button(
            onClick = {
                if (pagerState.currentPage == onboardingPages.lastIndex) {
                    finish()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (pagerState.currentPage == onboardingPages.lastIndex) "Get started" else "Next")
        }
    }
}
