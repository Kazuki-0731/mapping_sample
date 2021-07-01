package com.example.compose_study.ui.screen.home

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import com.example.compose_study.databinding.FragmentHomeBinding
import com.example.compose_study.ui.screen.replaceable.*

/**
 * FragmentとComposeの共存
 * https://github.com/takusan23/BottomFragmentComposeView
 *
 */
class HomeFragment : Fragment() {

    private lateinit var homeViewModel: HomeViewModel
    private var _binding: FragmentHomeBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)

        binding.apply {
            composeView.setContent {
                val activityStarter = fun(replaceableView: ActivityReplaceableView<*>) {
                    startActivity(Intent(requireActivity(), replaceableView.activityClass.java))
                }
                /**
                 * Bundle に保存可能なすべての値を自動的に保存します。
                 * その他の値については、カスタムのセーバーオブジェクトに渡すことができます。
                 */
                val navigator = rememberSaveable(
                    saver = Navigator.Saver(AllRootCategory, requireActivity().onBackPressedDispatcher, activityStarter)
                ) {
                    Navigator(AllRootCategory, requireActivity().onBackPressedDispatcher, activityStarter)
                }
                //状態は再コンポーズをまたいで保持
                val replaceableColors = remember {
                    ReplaceableColors().also {
                        lifecycle.addObserver(
                            LifecycleEventObserver { _, event ->
                                if (event == Lifecycle.Event.ON_RESUME) {
                                    it.loadColorsFromSharedPreferences(requireActivity())
                                }
                            }
                        )
                    }
                }
                ReplaceableTheme(replaceableColors, requireActivity().window) {
                    val filteringMode = rememberSaveable(
                        saver = FilterMode.Saver(requireActivity().onBackPressedDispatcher)
                    ) {
                        FilterMode(requireActivity().onBackPressedDispatcher)
                    }
                    val onStartFiltering = { filteringMode.isFiltering = true }
                    val onEndFiltering = { filteringMode.isFiltering = false }
                    ReplaceableApp(
                        currentView = navigator.currentReplaceableView,
                        backStackTitle = navigator.backStackTitle,
                        isFiltering = filteringMode.isFiltering,
                        onStartFiltering = onStartFiltering,
                        onEndFiltering = onEndFiltering,
                        onNavigateToReplaceable = { replaceable ->
                            if (filteringMode.isFiltering) {
                                onEndFiltering()
                                navigator.popAll()
                            }
                            navigator.navigateTo(replaceable)
                        },
                        canNavigateUp = !navigator.isRoot,
                        onNavigateUp = {
                            requireActivity().onBackPressed()
                        },
                        launchSettings = {
                            // startActivity(Intent(this, DemoSettingsActivity::class.java))
                        }
                    )
                }
            }
        }

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

@Composable
private fun ReplaceableTheme(
    replaceableColors: ReplaceableColors,
    window: Window,
    content: @Composable () -> Unit
) {
    MaterialTheme() {
        val statusBarColor = with(MaterialTheme.colors) {
            (if (isLight) primaryVariant else Color.Black).toArgb()
        }
        SideEffect {
            window.statusBarColor = statusBarColor
        }
        content()
    }
}

/**
 * Activityを継承したカスタムクラスを保存
 */
private class Navigator private constructor(
    private val backDispatcher: OnBackPressedDispatcher,
    private val launchActivityReplaceable: (ActivityReplaceableView<*>) -> Unit,
    private val rootReplaceableView: ReplaceableView,
    initialReplaceableView: ReplaceableView,
    private val backStack: MutableList<ReplaceableView>
) {
    constructor(
        rootReplaceableView: ReplaceableView,
        backDispatcher: OnBackPressedDispatcher,
        launchActivityReplaceable: (ActivityReplaceableView<*>) -> Unit
    ) : this(backDispatcher, launchActivityReplaceable, rootReplaceableView, rootReplaceableView, mutableListOf<ReplaceableView>())

    private val onBackPressed = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            popBackStack()
        }
    }.apply {
        isEnabled = !isRoot
        backDispatcher.addCallback(this)
    }

    private var _currentReplaceable by mutableStateOf(initialReplaceableView)
    var currentReplaceableView: ReplaceableView
        get() = _currentReplaceable
        private set(value) {
            _currentReplaceable = value
            onBackPressed.isEnabled = !isRoot
        }

    val isRoot: Boolean get() = backStack.isEmpty()

    val backStackTitle: String
        get() =
            (backStack.drop(1) + currentReplaceableView).joinToString(separator = " > ") { it.title }

    fun navigateTo(replaceableView: ReplaceableView) {
        if (replaceableView is ActivityReplaceableView<*>) {
            launchActivityReplaceable(replaceableView)
        } else {
            backStack.add(currentReplaceableView)
            currentReplaceableView = replaceableView
        }
    }

    fun popAll() {
        if (!isRoot) {
            backStack.clear()
            currentReplaceableView = rootReplaceableView
        }
    }

    private fun popBackStack() {
        currentReplaceableView = backStack.removeAt(backStack.lastIndex)
    }

    companion object {
        fun Saver(
            rootReplaceable: ReplaceableViewCategory,
            backDispatcher: OnBackPressedDispatcher,
            launchActivityReplaceable: (ActivityReplaceableView<*>) -> Unit
        ): Saver<Navigator, *> = listSaver<Navigator, String>(
            save = { navigator ->
                (navigator.backStack + navigator.currentReplaceableView).map { it.title }
            },
            restore = { restored ->
                require(restored.isNotEmpty())
                val backStack = restored.mapTo(mutableListOf()) {
                    requireNotNull(findView(rootReplaceable, it))
                }
                val initial = backStack.removeAt(backStack.lastIndex)
                Navigator(backDispatcher, launchActivityReplaceable, rootReplaceable, initial, backStack)
            }
        )

        private fun findView(replaceableView: ReplaceableView, title: String): ReplaceableView? {
            if (replaceableView.title == title) {
                return replaceableView
            }
            if (replaceableView is ReplaceableViewCategory) {
                replaceableView.replaceableViews.forEach { child ->
                    findView(child, title)
                        ?.let { return it }
                }
            }
            return null
        }
    }
}

private class FilterMode(backDispatcher: OnBackPressedDispatcher, initialValue: Boolean = false) {

    private var _isFiltering by mutableStateOf(initialValue)

    private val onBackPressed = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            isFiltering = false
        }
    }.apply {
        isEnabled = initialValue
        backDispatcher.addCallback(this)
    }

    var isFiltering
        get() = _isFiltering
        set(value) {
            _isFiltering = value
            onBackPressed.isEnabled = value
        }

    companion object {
        fun Saver(backDispatcher: OnBackPressedDispatcher) = Saver<FilterMode, Boolean>(
            save = { it.isFiltering },
            restore = { FilterMode(backDispatcher, it) }
        )
    }
}

/**
 * Returns a [ReplaceableColors] from the values saved to [SharedPreferences]. If a given color is
 * not present in the [SharedPreferences], its default value as defined in [Colors]
 * will be returned.
 */
fun ReplaceableColors.loadColorsFromSharedPreferences(context: Context) {

}

/**
 * TODO: remove after b/154329050 is fixed
 * Inline classes don't play well with reflection, so we want boxed classes for our
 * call to [lightColors].
 */
internal fun reflectLightColors(
    primary: Long = 0xFF6200EE,
    primaryVariant: Long = 0xFF3700B3,
    secondary: Long = 0xFF03DAC6,
    secondaryVariant: Long = 0xFF018786,
    background: Long = 0xFFFFFFFF,
    surface: Long = 0xFFFFFFFF,
    error: Long = 0xFFB00020,
    onPrimary: Long = 0xFFFFFFFF,
    onSecondary: Long = 0xFF000000,
    onBackground: Long = 0xFF000000,
    onSurface: Long = 0xFF000000,
    onError: Long = 0xFFFFFFFF
) = lightColors(
    primary = Color(primary),
    primaryVariant = Color(primaryVariant),
    secondary = Color(secondary),
    secondaryVariant = Color(secondaryVariant),
    background = Color(background),
    surface = Color(surface),
    error = Color(error),
    onPrimary = Color(onPrimary),
    onSecondary = Color(onSecondary),
    onBackground = Color(onBackground),
    onSurface = Color(onSurface),
    onError = Color(onError)
)

/**
 * TODO: remove after b/154329050 is fixed
 * Inline classes don't play well with reflection, so we want boxed classes for our
 * call to [darkColors].
 */
internal fun reflectDarkColors(
    primary: Long = 0xFFBB86FC,
    primaryVariant: Long = 0xFF3700B3,
    secondary: Long = 0xFF03DAC6,
    background: Long = 0xFF121212,
    surface: Long = 0xFF121212,
    error: Long = 0xFFCF6679,
    onPrimary: Long = 0xFF000000,
    onSecondary: Long = 0xFF000000,
    onBackground: Long = 0xFFFFFFFF,
    onSurface: Long = 0xFFFFFFFF,
    onError: Long = 0xFF000000
) = darkColors(
    primary = Color(primary),
    primaryVariant = Color(primaryVariant),
    secondary = Color(secondary),
    background = Color(background),
    surface = Color(surface),
    error = Color(error),
    onPrimary = Color(onPrimary),
    onSecondary = Color(onSecondary),
    onBackground = Color(onBackground),
    onSurface = Color(onSurface),
    onError = Color(onError)
)