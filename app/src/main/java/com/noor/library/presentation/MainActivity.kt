package com.noor.library.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.noor.library.NoorApplication
import com.noor.library.presentation.theme.NoorTheme

class MainActivity : ComponentActivity() {
    private val appVm: AppViewModel by viewModels {
        val app = application as NoorApplication
        VMFactory { AppViewModel(app.repository, app.importer) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { NoorRoot(appVm) }
    }
}

@Composable
fun NoorRoot(appVm: AppViewModel) {
    val settings by appVm.settings.collectAsState()
    val importState by appVm.importState.collectAsState()
    val s = settings
    NoorTheme(s?.theme ?: "dark") {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            when {
                s == null -> Unit
                importState is ImportState.Loading -> Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Text("در حال آماده‌سازی کتابخانه…", Modifier.padding(top = 16.dp))
                }
                importState is ImportState.Failed -> Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("خطا در آماده‌سازی محتوا")
                    Text((importState as ImportState.Failed).message, Modifier.padding(vertical = 12.dp))
                    Button(onClick = { appVm.runImport() }) { Text("تلاش دوباره") }
                }
                else -> NoorNav(appVm, s)
            }
        }
    }
}

@Composable
fun NoorNav(appVm: AppViewModel, settings: com.noor.library.data.Settings) {
    val nav = rememberNavController()
    val repo = appVm.repo

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                repo = repo, settings = settings,
                onOpenBook = { nav.navigate("book/$it") },
                onOpenReader = { b, t, n, p -> nav.navigate("reader/$b/$t/$n?passage=$p") },
                onSearch = { nav.navigate("search") },
                onLibrary = { nav.navigate("library") },
                onSettings = { nav.navigate("settings") },
            )
        }
        composable("book/{bookId}", arguments = listOf(navArgument("bookId") { type = NavType.StringType })) {
            val bookId = it.arguments?.getString("bookId") ?: "quran"
            BookScreen(
                repo = repo, bookId = bookId,
                onBack = { nav.popBackStack() },
                onOpenSection = { t, n -> nav.navigate("reader/$bookId/$t/$n?passage=1") }
            )
        }
        composable(
            "reader/{bookId}/{type}/{number}?passage={passage}",
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("type") { type = NavType.StringType },
                navArgument("number") { type = NavType.IntType },
                navArgument("passage") { type = NavType.IntType; defaultValue = 1 },
            )
        ) {
            val a = it.arguments!!
            ReaderScreen(
                appVm = appVm, settings = settings,
                bookId = a.getString("bookId") ?: "quran",
                type = a.getString("type") ?: "surah",
                number = a.getInt("number"),
                initialPassage = a.getInt("passage"),
                onBack = { nav.popBackStack() },
                onOpenSection = { b, t, n ->
                    nav.navigate("reader/$b/$t/$n?passage=1") { popUpTo("reader/{bookId}/{type}/{number}?passage={passage}") { inclusive = true } }
                }
            )
        }
        composable("search") {
            SearchScreen(
                repo = repo, settings = settings,
                onBack = { nav.popBackStack() },
                onOpenReader = { b, t, n, p -> nav.navigate("reader/$b/$t/$n?passage=$p") }
            )
        }
        composable("library") {
            LibraryScreen(
                repo = repo,
                onBack = { nav.popBackStack() },
                onOpenReader = { b, t, n, p -> nav.navigate("reader/$b/$t/$n?passage=$p") }
            )
        }
        composable("settings") {
            SettingsScreen(appVm = appVm, settings = settings, onBack = { nav.popBackStack() })
        }
    }
}
