package dev.gnacho.netgrip.presentation

import dev.gnacho.netgrip.discovery.DiscoveredRouter
import dev.gnacho.netgrip.discovery.RouterDiscovery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val router = DiscoveredRouter("rt2", "Router 2", "v1", "192.168.1.2", 8080)

private class FakeDiscovery(
    override val isSupported: Boolean = true,
    private val action: () -> List<DiscoveredRouter> = { listOf(router) },
) : RouterDiscovery {
    var calls = 0
        private set

    override suspend fun discover(): List<DiscoveredRouter> {
        calls += 1
        return action()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class DiscoveryViewModelTest {

    @Test
    fun searchPublishesResultsAndSelectionIsExplicit() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val discovery = FakeDiscovery()
            val viewModel = DiscoveryViewModel(discovery)
            assertNull(viewModel.selectedRouter)
            viewModel.search()
            assertEquals(DiscoveryStatus.Searching, viewModel.status)
            testScheduler.advanceUntilIdle()
            assertEquals(listOf(router), viewModel.routers)
            assertEquals(DiscoveryStatus.Complete, viewModel.status)
            assertNull(viewModel.selectedRouter)
            viewModel.select(router)
            assertEquals(router, viewModel.selectedRouter)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun aDifferentRouterCannotBeSelected() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = DiscoveryViewModel(FakeDiscovery())
            viewModel.search()
            testScheduler.advanceUntilIdle()
            viewModel.select(router.copy(id = "other"))
            assertNull(viewModel.selectedRouter)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun discoveryFailureProducesErrorWithoutResults() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = DiscoveryViewModel(FakeDiscovery(action = { error("network down") }))
            viewModel.search()
            testScheduler.advanceUntilIdle()
            assertEquals(DiscoveryStatus.Error, viewModel.status)
            assertEquals(emptyList(), viewModel.routers)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun emptyDiscoveryCompletesWithoutInventingRouters() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = DiscoveryViewModel(FakeDiscovery(action = { emptyList() }))
            viewModel.search()
            testScheduler.advanceUntilIdle()
            assertEquals(DiscoveryStatus.Complete, viewModel.status)
            assertEquals(emptyList(), viewModel.routers)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun unsupportedPlatformNeverStartsDiscovery() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val discovery = FakeDiscovery(isSupported = false)
            val viewModel = DiscoveryViewModel(discovery)
            viewModel.search()
            testScheduler.advanceUntilIdle()
            assertEquals(0, discovery.calls)
            assertEquals(DiscoveryStatus.Idle, viewModel.status)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
