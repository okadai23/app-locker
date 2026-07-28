package com.daisuke.readinglock.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daisuke.readinglock.data.Source
import com.daisuke.readinglock.ui.block.AddAppsScreen
import com.daisuke.readinglock.ui.block.AddSiteScreen
import com.daisuke.readinglock.ui.block.BlockScreen
import com.daisuke.readinglock.ui.books.BookListScreen
import com.daisuke.readinglock.ui.flow.BookDeclareScreen
import com.daisuke.readinglock.ui.flow.FlowViewModel
import com.daisuke.readinglock.ui.flow.MeasuringScreen
import com.daisuke.readinglock.ui.flow.SourceSelectScreen
import com.daisuke.readinglock.ui.history.HistoryScreen
import com.daisuke.readinglock.ui.home.HomeScreen
import com.daisuke.readinglock.ui.setup.SetupScreen

object Routes {
    const val HOME = "home"
    const val SETUP = "setup"
    const val FLOW_SOURCE = "flow/source"
    const val FLOW_BOOK = "flow/book/{source}"
    const val FLOW_MEASURE = "flow/measure"
    const val BOOKS = "books"
    const val BLOCK = "block"
    const val BLOCK_ADD_APPS = "block/apps/add"
    const val BLOCK_ADD_SITE = "block/site/add"
    const val HISTORY = "history"

    fun book(source: Source) = "flow/book/${source.name}"
}

/**
 * 単一 Activity のナビゲーション。オーバーレイ・通知からは reading://start / reading://home で入る。
 */
@Composable
fun ReadingLockNav(
    startAtSetup: Boolean,
    deepLink: String?,
    onDeepLinkHandled: () -> Unit,
    onRequestVpn: () -> Unit,
    nav: NavHostController = rememberNavController(),
) {
    LaunchedEffect(deepLink) {
        val target = deepLink ?: return@LaunchedEffect
        onDeepLinkHandled()
        nav.navigate(target) {
            popUpTo(Routes.HOME) { inclusive = target == Routes.HOME }
            launchSingleTop = true
        }
    }

    NavHost(nav, startDestination = if (startAtSetup) Routes.SETUP else Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onStartReading = { nav.navigate(Routes.FLOW_SOURCE) },
                onOpenSession = { nav.navigate(Routes.FLOW_MEASURE) },
                onNavigate = { nav.navigate(it) },
            )
        }
        composable(Routes.SETUP) {
            SetupScreen(
                onDone = {
                    nav.navigate(Routes.HOME) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onRequestVpn = onRequestVpn,
            )
        }
        composable(Routes.FLOW_SOURCE) {
            val flow: FlowViewModel = viewModel { FlowViewModel() }
            SourceSelectScreen(
                onBack = { nav.popBackStack() },
                onSelect = { source ->
                    // 紙の本は宣言する本が任意なので、ここで開始してそのまま計測へ。§4.2
                    if (source == Source.PAPER) {
                        flow.start(null, Source.PAPER)
                        nav.navigate(Routes.FLOW_MEASURE) { popUpTo(Routes.HOME) }
                    } else {
                        nav.navigate(Routes.book(source))
                    }
                },
            )
        }
        composable(
            Routes.FLOW_BOOK,
            arguments = listOf(navArgument("source") { type = NavType.StringType }),
        ) { entry ->
            val source = Source.valueOf(entry.arguments?.getString("source") ?: Source.KINDLE.name)
            BookDeclareScreen(
                source = source,
                onBack = { nav.popBackStack() },
                onStarted = { nav.navigate(Routes.FLOW_MEASURE) { popUpTo(Routes.HOME) } },
            )
        }
        composable(Routes.FLOW_MEASURE) {
            MeasuringScreen(onFinished = {
                nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
            })
        }
        composable(Routes.BOOKS) { BookListScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.BLOCK) {
            BlockScreen(
                onBack = { nav.popBackStack() },
                onAddApps = { nav.navigate(Routes.BLOCK_ADD_APPS) },
                onAddSite = { nav.navigate(Routes.BLOCK_ADD_SITE) },
            )
        }
        composable(Routes.BLOCK_ADD_APPS) { AddAppsScreen(onClose = { nav.popBackStack() }) }
        composable(Routes.BLOCK_ADD_SITE) { AddSiteScreen(onClose = { nav.popBackStack() }) }
        composable(Routes.HISTORY) { HistoryScreen(onBack = { nav.popBackStack() }) }
    }
}
