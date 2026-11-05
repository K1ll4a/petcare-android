package com.petcare.app

import com.petcare.app.data.fake.DemoAccountRepository
import com.petcare.app.domain.model.*
import com.petcare.app.domain.repository.AccountRepository
import com.petcare.app.presentation.account.AccountController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import kotlinx.io.IOException

class AccountControllerTest {
    private suspend fun awaitIdle(controller: AccountController) {
        withTimeout(5000) { while (controller.state.value.busy) delay(5) }
    }
    @Test fun loginSelectLogout_clearsProtectedState() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val controller = AccountController(scope, DemoAccountRepository(MemoryDemoStore(), delayMs = 0))
            controller.login("grisha@example.com", "demo12345"); awaitIdle(controller)
            assertEquals(2, controller.state.value.spaces.size)
            controller.selectSpace(1); assertEquals(1L, controller.state.value.selectedSpaceId)
            controller.loadInvitations(1); awaitIdle(controller)
            controller.createInvitation(1); awaitIdle(controller)
            assertTrue(controller.state.value.invitations.isNotEmpty())
            controller.logout()
            assertNull(controller.state.value.session); assertNull(controller.state.value.selectedSpaceId)
            assertTrue(controller.state.value.spaces.isEmpty()); assertTrue(controller.state.value.invitations.isEmpty())
            controller.selectSpace(1); assertNull(controller.state.value.selectedSpaceId)
        } finally { scope.cancel() }
    }
    @Test fun networkError_hasReadableMessage_andRetry() = runBlocking {
        val accounts = DemoAccountRepository(MemoryDemoStore(), delayMs = 0)
        accounts.login("grisha@example.com", "demo12345")
        var fail = true
        val repository = object : AccountRepository by accounts {
            override suspend fun getSpaces(): List<Space> {
                if (fail) throw IOException("offline")
                return accounts.getSpaces()
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val c = AccountController(scope, repository); awaitIdle(c)
            assertTrue(c.state.value.error!!.contains("Нет соединения"))
            fail = false; c.retry(); awaitIdle(c)
            assertEquals(2, c.state.value.spaces.size); assertNull(c.state.value.error)
        } finally { scope.cancel() }
    }
    @Test fun logout_discardsNonCancellableLateResponse() = runBlocking {
        val accounts = DemoAccountRepository(MemoryDemoStore(), delayMs = 0)
        accounts.login("grisha@example.com", "demo12345")
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val repository = object : AccountRepository by accounts {
            override suspend fun getSpaces(): List<Space> = withContext(NonCancellable) {
                val result = accounts.getSpaces(); entered.complete(Unit); release.await(); result
            }
        }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val c = AccountController(scope, repository); withTimeout(5000) { entered.await() }
            c.logout(); release.complete(Unit); delay(50)
            assertNull(c.state.value.session); assertTrue(c.state.value.spaces.isEmpty())
        } finally { scope.cancel() }
    }
}
