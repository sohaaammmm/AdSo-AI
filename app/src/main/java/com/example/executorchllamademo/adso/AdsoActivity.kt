package com.example.executorchllamademo.adso

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.lifecycleScope
import com.example.executorchllamademo.LogsActivity
import com.example.executorchllamademo.ModelSettingsActivity
import com.example.executorchllamademo.adso.llm.LlamaEngine
import com.example.executorchllamademo.adso.ui.AddCourseScreen
import com.example.executorchllamademo.adso.ui.AdsoBackground
import com.example.executorchllamademo.adso.ui.BottomTabs
import com.example.executorchllamademo.adso.ui.ChatScreen
import com.example.executorchllamademo.adso.ui.CoursesScreen
import com.example.executorchllamademo.adso.ui.GapsScreen
import com.example.executorchllamademo.adso.ui.GeneratingScreen
import com.example.executorchllamademo.adso.ui.GoalScreen
import com.example.executorchllamademo.adso.ui.HomeScreen
import com.example.executorchllamademo.adso.ui.LessonScreen
import com.example.executorchllamademo.adso.ui.LocalLang
import com.example.executorchllamademo.adso.ui.PlanScreen
import com.example.executorchllamademo.adso.ui.ProgressScreen
import com.example.executorchllamademo.adso.ui.QuizScreen
import com.example.executorchllamademo.adso.ui.ResultScreen
import com.example.executorchllamademo.adso.ui.SettingsScreen
import com.example.executorchllamademo.adso.ui.SkillCheckScreen
import com.example.executorchllamademo.adso.ui.Tab
import com.example.executorchllamademo.adso.ui.WelcomeScreen
import com.example.executorchllamademo.ui.theme.AdsoTheme
import kotlinx.coroutines.launch

/** AdSo's single activity. Screens are switched by [AdsoViewModel.screen] (hand-written navigation). */
class AdsoActivity : ComponentActivity() {
    private val vm: AdsoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            AdsoTheme(darkTheme = vm.profile.darkTheme) {
                val lang = vm.profile.language
                CompositionLocalProvider(
                    LocalLang provides lang,
                    LocalLayoutDirection provides if (lang == "ur") LayoutDirection.Rtl else LayoutDirection.Ltr,
                ) {
                    AdsoBackground {
                        if (vm.ready) AdsoApp(vm, { openModelSettings() }, ::openLogs)
                    }
                }
            }
        }
    }

    /** The original ExecuTorch model screen (advanced options). AdSo releases its model first. */
    private fun openModelSettings() = lifecycleScope.launch {
        LlamaEngine.releaseNow()
        startActivity(Intent(this@AdsoActivity, ModelSettingsActivity::class.java))
    }

    private fun openLogs() = startActivity(Intent(this, LogsActivity::class.java))
}

@Composable
private fun AdsoApp(vm: AdsoViewModel, openAdvanced: () -> Unit, openLogs: () -> Unit) {
    val screen = vm.screen
    BackHandler(enabled = screen !in setOf(Screen.WELCOME, Screen.HOME, Screen.GENERATING)) { vm.back() }
    val tab = when (screen) {
        Screen.HOME -> Tab.HOME
        Screen.PLAN -> Tab.PLAN
        Screen.PROGRESS -> Tab.PROGRESS
        Screen.SETTINGS -> Tab.SETTINGS
        else -> null
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            AnimatedContent(screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { s ->
                when (s) {
                    Screen.WELCOME -> WelcomeScreen(vm)
                    Screen.PROFILE -> GoalScreen(vm, editing = false)
                    Screen.EDIT_GOALS -> GoalScreen(vm, editing = true)
                    Screen.COURSES -> CoursesScreen(vm)
                    Screen.ADD_COURSE -> AddCourseScreen(vm)
                    Screen.CHECK -> SkillCheckScreen(vm)
                    Screen.GAPS -> GapsScreen(vm)
                    Screen.GENERATING -> GeneratingScreen(vm)
                    Screen.HOME -> HomeScreen(vm)
                    Screen.PLAN -> PlanScreen(vm)
                    Screen.PROGRESS -> ProgressScreen(vm)
                    Screen.SETTINGS -> SettingsScreen(vm, openAdvanced, openLogs)
                    Screen.LESSON -> LessonScreen(vm)
                    Screen.QUIZ -> QuizScreen(vm)
                    Screen.RESULT -> ResultScreen(vm)
                    Screen.CHAT -> ChatScreen(vm)
                }
            }
        }
        if (tab != null) {
            BottomTabs(tab) { t ->
                vm.go(
                    when (t) {
                        Tab.HOME -> Screen.HOME
                        Tab.PLAN -> Screen.PLAN
                        Tab.PROGRESS -> Screen.PROGRESS
                        Tab.SETTINGS -> Screen.SETTINGS
                    }
                )
            }
        }
    }
}
