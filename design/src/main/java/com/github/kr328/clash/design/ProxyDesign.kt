package com.github.kr328.clash.design

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.viewpager2.widget.ViewPager2
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.TunnelState
import com.github.kr328.clash.design.adapter.ProxyAdapter
import com.github.kr328.clash.design.adapter.ProxyPageAdapter
import com.github.kr328.clash.design.component.ProxyMenu
import com.github.kr328.clash.design.component.ProxyViewConfig
import com.github.kr328.clash.design.databinding.DesignProxyBinding
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.design.store.UiStore
import com.github.kr328.clash.design.util.applyFrom
import com.github.kr328.clash.design.util.getPixels
import com.github.kr328.clash.design.util.layoutInflater
import com.github.kr328.clash.design.util.resolveThemedColor
import com.github.kr328.clash.design.util.root
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProxyDesign(
    context: Context,
    overrideMode: TunnelState.Mode?,
    private val pageNames: List<String>,
    uiStore: UiStore,
) : Design<ProxyDesign.Request>(context) {
    enum class AutoSelectMetric {
        Download, Upload, Delay
    }

    enum class AutoSelectSort {
        Global, Region
    }

    companion object {
        const val AUTO_SELECT_GROUP = "__clash_auto_select__"
    }

    sealed class Request {
        object ReloadAll : Request()
        object ReLaunch : Request()

        data class PatchMode(val mode: TunnelState.Mode?) : Request()
        data class Reload(val index: Int, val scrollToTop: Boolean = false) : Request()
        data class Select(val index: Int, val name: String) : Request()
        data class UrlTest(val index: Int) : Request()
    }

    private val binding = DesignProxyBinding
        .inflate(context.layoutInflater, context.root, false)

    private val config = ProxyViewConfig(context, uiStore.proxySingleLine)
    private val autoConfig = ProxyViewConfig(context, true)

    private val menu: ProxyMenu by lazy {
        ProxyMenu(context, binding.menuView, overrideMode, uiStore, requests) {
            config.singleLine = uiStore.proxySingleLine
        }
    }

    private val adapter: ProxyPageAdapter
        get() = binding.pagesView.adapter!! as ProxyPageAdapter

    var autoSelectMetric: AutoSelectMetric = AutoSelectMetric.Download
        private set
    var autoSelectSort: AutoSelectSort = AutoSelectSort.Global
        private set

    private var horizontalScrolling = false
    private val verticalBottomScrolled: Boolean
        get() = adapter.states.getOrNull(binding.pagesView.currentItem)?.bottom ?: false
    private var urlTesting: Boolean
        get() = adapter.states.getOrNull(binding.pagesView.currentItem)?.urlTesting ?: false
        set(value) {
            adapter.states.getOrNull(binding.pagesView.currentItem)?.urlTesting = value
        }

    override val root: View = binding.root

    suspend fun updateGroup(
        position: Int,
        proxies: List<Proxy>,
        selectable: Boolean,
        parent: ProxyState,
        links: Map<String, ProxyState>,
        finishTesting: Boolean = true,
        scrollToTop: Boolean = false,
    ) {
        adapter.updateAdapter(position, proxies, selectable, parent, links)

        if (scrollToTop) {
            adapter.scrollToTop(position)
        }

        if (finishTesting) {
            adapter.states[position].urlTesting = false
        }

        updateUrlTestButtonStatus()
    }

    suspend fun requestRedrawVisible() {
        withContext(Dispatchers.Main) {
            adapter.requestRedrawVisible()
        }
    }

    suspend fun requestDonate() {
        withContext(Dispatchers.Main) {
            val title = context.getText(R.string.request_donate)
            val message = context.getText(R.string.request_donate_tips)

            if (title.isNotEmpty() && message.isNotEmpty()) {
                MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.request_donate)
                    .setMessage(R.string.request_donate_tips)
                    .setPositiveButton(R.string.ok) { _, _ -> }
                    .setCancelable(true)
                    .show()
            }
        }
    }

    suspend fun showModeSwitchTips() {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, R.string.mode_switch_tips, Toast.LENGTH_LONG).show()
        }
    }

    suspend fun showAutoSelected(name: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                context.getString(R.string.auto_selected_best, name),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    suspend fun showAutoSelectFailed() {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, R.string.auto_select_failed, Toast.LENGTH_SHORT).show()
        }
    }

    suspend fun showSpeedTestFailed() {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, R.string.speed_test_failed, Toast.LENGTH_SHORT).show()
        }
    }

    init {
        binding.self = this

        binding.activityBarLayout.applyFrom(context)

        binding.menuView.setOnClickListener {
            menu.show()
        }

        binding.testModeView.setOnClickListener {
            showTestModeMenu()
        }

        binding.sortModeView.setOnClickListener {
            showSortModeMenu()
        }

        if (pageNames.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE

            binding.urlTestView.visibility = View.GONE
            binding.tabLayoutView.visibility = View.GONE
            binding.elevationView.visibility = View.GONE
            binding.pagesView.visibility = View.GONE
            binding.urlTestFloatView.visibility = View.GONE
        } else {
            binding.urlTestFloatView.supportImageTintList = ColorStateList.valueOf(
                context.resolveThemedColor(R.attr.colorOnPrimary)
            )

            binding.pagesView.apply {
                val pageConfigs = pageNames.map {
                    if (it == AUTO_SELECT_GROUP) autoConfig else config
                }
                val autoControlsHeight = context.getPixels(R.dimen.auto_select_controls_height)
                val pageTopInsets = pageNames.map {
                    if (it == AUTO_SELECT_GROUP) autoControlsHeight else 0
                }

                adapter = ProxyPageAdapter(
                    surface,
                    pageConfigs,
                    List(pageNames.size) { index ->
                        ProxyAdapter(pageConfigs[index]) { name ->
                            requests.trySend(Request.Select(index, name))
                        }
                    },
                    pageTopInsets,
                ) {
                    if (it == currentItem)
                        updateUrlTestButtonStatus()
                }

                registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                    override fun onPageScrollStateChanged(state: Int) {
                        horizontalScrolling = state != ViewPager2.SCROLL_STATE_IDLE

                        updateUrlTestButtonStatus()
                    }

                    override fun onPageSelected(position: Int) {
                        uiStore.proxyLastGroup = pageNames[position]

                        updateAutoSelectControls()

                        if (position == autoSelectPosition()) {
                            requests.trySend(Request.Reload(position))
                        }
                    }
                })
            }

            TabLayoutMediator(binding.tabLayoutView, binding.pagesView) { tab, index ->
                tab.text = pageText(pageNames[index])
            }.attach()

            val initialPosition = pageNames.indexOf(uiStore.proxyLastGroup)
                .takeIf { it >= 0 } ?: 0

            binding.pagesView.post {
                if (binding.pagesView.currentItem != initialPosition)
                    binding.pagesView.setCurrentItem(initialPosition, false)

                updateAutoSelectControls()
            }
        }
    }

    private fun pageText(name: String): String {
        return if (name == AUTO_SELECT_GROUP) {
            context.getString(R.string.auto_select_proxy)
        } else {
            name
        }
    }

    private fun autoSelectPosition(): Int = pageNames.indexOf(AUTO_SELECT_GROUP)

    private fun updateAutoSelectControls() {
        val position = autoSelectPosition()

        binding.autoSelectControls.visibility =
            if (position >= 0 && binding.pagesView.currentItem == position) View.VISIBLE else View.GONE

        updateAutoSelectButtonText()
    }

    private fun updateAutoSelectButtonText() {
        val testMode = context.getString(
            when (autoSelectMetric) {
                AutoSelectMetric.Download -> R.string.speed_test_download_short
                AutoSelectMetric.Upload -> R.string.speed_test_upload_short
                AutoSelectMetric.Delay -> R.string.speed_test_latency_short
            }
        )
        val sortMode = context.getString(
            when (autoSelectSort) {
                AutoSelectSort.Global -> R.string.sort_global_short
                AutoSelectSort.Region -> R.string.sort_region_short
            }
        )

        binding.testModeView.text = context.getString(R.string.auto_select_test_mode, testMode)
        binding.sortModeView.text = context.getString(R.string.auto_select_sort_mode, sortMode)
    }

    private fun showTestModeMenu() {
        val popup = PopupMenu(context, binding.testModeView)

        popup.menuInflater.inflate(R.menu.menu_auto_select_test, popup.menu)
        popup.menu.findItem(R.id.auto_test_download).isChecked =
            autoSelectMetric == AutoSelectMetric.Download
        popup.menu.findItem(R.id.auto_test_upload).isChecked =
            autoSelectMetric == AutoSelectMetric.Upload
        popup.menu.findItem(R.id.auto_test_delay).isChecked =
            autoSelectMetric == AutoSelectMetric.Delay

        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.auto_test_download -> autoSelectMetric = AutoSelectMetric.Download
                R.id.auto_test_upload -> autoSelectMetric = AutoSelectMetric.Upload
                R.id.auto_test_delay -> autoSelectMetric = AutoSelectMetric.Delay
                else -> return@setOnMenuItemClickListener false
            }

            updateAutoSelectButtonText()
            autoSelectPosition().takeIf { it >= 0 }?.let {
                requests.trySend(Request.Reload(it, scrollToTop = true))
            }

            true
        }

        popup.show()
    }

    private fun showSortModeMenu() {
        val popup = PopupMenu(context, binding.sortModeView)

        popup.menuInflater.inflate(R.menu.menu_auto_select_sort, popup.menu)
        popup.menu.findItem(R.id.auto_sort_global).isChecked =
            autoSelectSort == AutoSelectSort.Global
        popup.menu.findItem(R.id.auto_sort_region).isChecked =
            autoSelectSort == AutoSelectSort.Region

        popup.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.auto_sort_global -> autoSelectSort = AutoSelectSort.Global
                R.id.auto_sort_region -> autoSelectSort = AutoSelectSort.Region
                else -> return@setOnMenuItemClickListener false
            }

            updateAutoSelectButtonText()
            autoSelectPosition().takeIf { it >= 0 }?.let {
                requests.trySend(Request.Reload(it, scrollToTop = true))
            }

            true
        }

        popup.show()
    }

    fun requestUrlTesting() {
        val position = binding.pagesView.currentItem

        if (position < 0 || adapter.states[position].urlTesting)
            return

        urlTesting = true

        if (position == autoSelectPosition()) {
            Toast.makeText(context, R.string.speed_testing_all, Toast.LENGTH_SHORT).show()
        }

        requests.trySend(Request.UrlTest(position))

        updateUrlTestButtonStatus()
    }

    private fun updateUrlTestButtonStatus() {
        updateAutoSelectControls()

        val controlsEnabled = !urlTesting
        binding.testModeView.isEnabled = controlsEnabled
        binding.sortModeView.isEnabled = controlsEnabled
        binding.autoSelectControls.alpha = if (controlsEnabled) 1f else 0.55f

        if (verticalBottomScrolled || horizontalScrolling || urlTesting) {
            binding.urlTestFloatView.hide()
        } else {
            binding.urlTestFloatView.show()
        }

        if (urlTesting) {
            binding.urlTestView.visibility = View.GONE
            binding.urlTestProgressView.visibility = View.VISIBLE
        } else {
            binding.urlTestView.visibility = View.VISIBLE
            binding.urlTestProgressView.visibility = View.GONE
        }
    }
}
