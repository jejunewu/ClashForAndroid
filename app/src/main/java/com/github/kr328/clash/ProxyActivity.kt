package com.github.kr328.clash

import com.github.kr328.clash.common.util.intent
import com.github.kr328.clash.core.Clash
import com.github.kr328.clash.core.model.Proxy
import com.github.kr328.clash.core.model.SpeedTestMode
import com.github.kr328.clash.design.ProxyDesign
import com.github.kr328.clash.design.model.ProxyState
import com.github.kr328.clash.store.TipsStore
import com.github.kr328.clash.util.withClash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.TimeUnit

class ProxyActivity : BaseActivity<ProxyDesign>() {
    override suspend fun main() {
        val mode = withClash { queryOverride(Clash.OverrideSlot.Session).mode }
        val names = withClash { queryProxyGroupNames(uiStore.proxyExcludeNotSelectable) }
        val hasAutoPage = names.isNotEmpty()
        val pageNames = if (hasAutoPage) {
            listOf(ProxyDesign.AUTO_SELECT_GROUP) + names
        } else {
            emptyList()
        }
        val groupPageOffset = if (hasAutoPage) 1 else 0
        val states = List(names.size) { ProxyState("?") }
        val autoState = ProxyState("")
        val unorderedStates = names.indices.map { names[it] to states[it] }.toMap()
        val reloadLock = Semaphore(10)
        val tips = TipsStore(this)

        val design = ProxyDesign(
            this,
            mode,
            pageNames,
            uiStore
        )

        setContentDesign(design)

        launch(Dispatchers.IO) {
            val pkg = packageManager.getPackageInfo(packageName, 0)
            val validate = System.currentTimeMillis() - pkg.firstInstallTime > TimeUnit.DAYS.toMillis(5)

            if (tips.requestDonate && validate) {
                tips.requestDonate = false

                design.requestDonate()
            }
        }

        design.requests.send(ProxyDesign.Request.ReloadAll)

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ProfileLoaded -> {
                            val newNames = withClash {
                                queryProxyGroupNames(uiStore.proxyExcludeNotSelectable)
                            }

                            if (newNames != names) {
                                startActivity(ProxyActivity::class.intent)

                                finish()
                            }
                        }
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        ProxyDesign.Request.ReLaunch -> {
                            startActivity(ProxyActivity::class.intent)

                            finish()
                        }
                        ProxyDesign.Request.ReloadAll -> {
                            names.indices.forEach { idx ->
                                design.requests.trySend(
                                    ProxyDesign.Request.Reload(idx + groupPageOffset)
                                )
                            }
                        }
                        is ProxyDesign.Request.Reload -> {
                            val request = it
                            val groupIndex = request.index - groupPageOffset

                            if (pageNames.getOrNull(request.index) == ProxyDesign.AUTO_SELECT_GROUP) {
                                launch {
                                    val group = withClash { queryAllProxies() }
                                    val sorted = sortProxies(
                                        group.proxies,
                                        design.autoSelectMetric,
                                        design.autoSelectSort
                                    )

                                    design.updateGroup(
                                        request.index,
                                        sorted,
                                        true,
                                        autoState,
                                        unorderedStates,
                                        finishTesting = false
                                    )
                                }
                            } else if (groupIndex in names.indices) {
                                launch {
                                    val group = reloadLock.withPermit {
                                        withClash {
                                            queryProxyGroup(names[groupIndex], uiStore.proxySort)
                                        }
                                    }
                                    val state = states[groupIndex]

                                    state.now = group.now

                                    design.updateGroup(
                                        request.index,
                                        group.proxies,
                                        group.type == Proxy.Type.Selector,
                                        state,
                                        unorderedStates
                                    )
                                }
                            }
                        }
                        is ProxyDesign.Request.Select -> {
                            val request = it
                            val groupIndex = request.index - groupPageOffset

                            if (pageNames.getOrNull(request.index) == ProxyDesign.AUTO_SELECT_GROUP) {
                                launch {
                                    val selectedGroup = withClash {
                                        autoSelectProxy(request.name)
                                    }

                                    if (selectedGroup != null) {
                                        autoState.now = request.name

                                        val index = names.indexOf(selectedGroup)
                                        if (index >= 0) {
                                            states[index].now = request.name
                                        }

                                        design.requestRedrawVisible()
                                    } else {
                                        design.showAutoSelectFailed()
                                    }
                                }
                            } else if (groupIndex in names.indices) {
                                withClash {
                                    patchSelector(names[groupIndex], request.name)

                                    states[groupIndex].now = request.name
                                }

                                design.requestRedrawVisible()
                            }
                        }
                        is ProxyDesign.Request.UrlTest -> {
                            val request = it
                            val groupIndex = request.index - groupPageOffset

                            if (pageNames.getOrNull(request.index) == ProxyDesign.AUTO_SELECT_GROUP) {
                                launch {
                                    val metric = design.autoSelectMetric
                                    val sort = design.autoSelectSort
                                    val mode = when (metric) {
                                        ProxyDesign.AutoSelectMetric.Download -> SpeedTestMode.Download
                                        ProxyDesign.AutoSelectMetric.Upload -> SpeedTestMode.Upload
                                        ProxyDesign.AutoSelectMetric.Delay -> SpeedTestMode.Delay
                                    }

                                    val initial = withClash { queryAllProxies() }
                                    design.updateGroup(
                                        request.index,
                                        sortProxies(initial.proxies, metric, sort),
                                        true,
                                        autoState,
                                        unorderedStates,
                                        finishTesting = false
                                    )

                                    val group = withClash { speedTest(mode) }
                                    val sorted = sortProxies(
                                        group.proxies,
                                        metric,
                                        design.autoSelectSort
                                    )

                                    design.updateGroup(
                                        request.index,
                                        sorted,
                                        true,
                                        autoState,
                                        unorderedStates
                                    )

                                    val best = sorted.firstOrNull { isValidMetric(it, metric) }
                                    if (best != null) {
                                        val selectedGroup = withClash {
                                            autoSelectProxy(best.name)
                                        }

                                        autoState.now = best.name

                                        val index = selectedGroup?.let { names.indexOf(it) } ?: -1
                                        if (index >= 0) {
                                            states[index].now = best.name
                                        }

                                        design.requestRedrawVisible()
                                        design.showAutoSelected(best.name)
                                    } else {
                                        design.showSpeedTestFailed()
                                    }
                                }
                            } else if (groupIndex in names.indices) {
                                launch {
                                    withClash {
                                        healthCheck(names[groupIndex])
                                    }

                                    design.requests.send(ProxyDesign.Request.Reload(request.index))
                                }
                            }
                        }
                        is ProxyDesign.Request.PatchMode -> {
                            design.showModeSwitchTips()

                            withClash {
                                val o = queryOverride(Clash.OverrideSlot.Session)

                                o.mode = it.mode

                                patchOverride(Clash.OverrideSlot.Session, o)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun sortProxies(
        proxies: List<Proxy>,
        metric: ProxyDesign.AutoSelectMetric,
        sort: ProxyDesign.AutoSelectSort
    ): List<Proxy> {
        val comparator = metricComparator(metric)

        if (sort == ProxyDesign.AutoSelectSort.Global) {
            return proxies.sortedWith(comparator)
        }

        return proxies.groupBy { it.region.ifEmpty { "其他" } }
            .values
            .sortedWith(Comparator<List<Proxy>> { left, right ->
                val leftBest = left.minWithOrNull(comparator)
                val rightBest = right.minWithOrNull(comparator)

                when {
                    leftBest == null && rightBest == null -> 0
                    leftBest == null -> 1
                    rightBest == null -> -1
                    else -> comparator.compare(leftBest, rightBest)
                }
            })
            .flatMap { it.sortedWith(comparator) }
    }

    private fun metricComparator(metric: ProxyDesign.AutoSelectMetric): Comparator<Proxy> {
        return Comparator<Proxy> { left, right ->
            val leftValid = isValidMetric(left, metric)
            val rightValid = isValidMetric(right, metric)

            when {
                leftValid && !rightValid -> -1
                !leftValid && rightValid -> 1
                !leftValid && !rightValid -> left.name.compareTo(right.name)
                else -> {
                    val leftValue = metricValue(left, metric)
                    val rightValue = metricValue(right, metric)
                    val result = if (metric == ProxyDesign.AutoSelectMetric.Delay) {
                        leftValue.compareTo(rightValue)
                    } else {
                        rightValue.compareTo(leftValue)
                    }

                    if (result != 0) result else left.name.compareTo(right.name)
                }
            }
        }
    }

    private fun isValidMetric(proxy: Proxy, metric: ProxyDesign.AutoSelectMetric): Boolean {
        return when (metric) {
            ProxyDesign.AutoSelectMetric.Download,
            ProxyDesign.AutoSelectMetric.Upload -> metricValue(proxy, metric) > 0
            ProxyDesign.AutoSelectMetric.Delay -> metricValue(proxy, metric) in
                    0L..Short.MAX_VALUE.toLong()
        }
    }

    private fun metricValue(proxy: Proxy, metric: ProxyDesign.AutoSelectMetric): Long {
        return when (metric) {
            ProxyDesign.AutoSelectMetric.Download -> proxy.download
            ProxyDesign.AutoSelectMetric.Upload -> proxy.upload
            ProxyDesign.AutoSelectMetric.Delay -> proxy.delay.toLong()
        }
    }
}
