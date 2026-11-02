package dev.grove.reader

import android.app.Application
import android.content.pm.ServiceInfo
import android.os.Looper
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class DocumentDownloadServiceTest {
    @Test fun aFastCachedFileStillPromotesTheServiceBeforeStoppingIt() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val job=DocumentDownloadService.prepare(app,"cached.pdf"){}.apply{start()}
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(job.isCompleted)
        assertNull("must not stop an unpromoted startForegroundService",shadowOf(app).nextStoppedService)
        val controller=Robolectric.buildService(DocumentDownloadService::class.java).create().startCommand(0,1)
        try {
            assertTrue(shadowOf(controller.get()).isLastForegroundNotificationAttached)
            assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        }finally{controller.destroy();shadowOf(Looper.getMainLooper()).idle()}
        assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
    }
    @Test fun screenOffKeepsPartialWakeLockUntilLastDownloadFinishes() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val gate=CompletableDeferred<Unit>()
        val job=DocumentDownloadService.prepare(app,"book.epub"){gate.await()}.apply{start()}
        val controller=Robolectric.buildService(DocumentDownloadService::class.java).create().startCommand(0,1)
        val wakeLock=ShadowPowerManager.getLatestWakeLock()
        try {
            val power=app.getSystemService(PowerManager::class.java)
            shadowOf(power).setIsInteractive(false)
            assertFalse(power.isInteractive)
            assertTrue(wakeLock.isHeld)
            assertTrue(shadowOf(controller.get()).isLastForegroundNotificationAttached)
            assertFalse(job.isCompleted)
            gate.complete(Unit);shadowOf(Looper.getMainLooper()).idle()
            assertTrue(job.isCompleted)
            assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        }finally{job.cancel();controller.destroy();shadowOf(Looper.getMainLooper()).idle()}
        assertFalse(wakeLock.isHeld)
    }
    @Test fun finishingOneOfTwoTransfersDoesNotStopProtectionForTheOther() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        val first=CompletableDeferred<Unit>();val second=CompletableDeferred<Unit>()
        val a=DocumentDownloadService.prepare(app,"one.pdf"){first.await()}.apply{start()}
        val b=DocumentDownloadService.prepare(app,"two.epub"){second.await()}.apply{start()}
        val controller=Robolectric.buildService(DocumentDownloadService::class.java).create().startCommand(0,1)
        try {
            first.complete(Unit);shadowOf(Looper.getMainLooper()).idle()
            assertTrue(a.isCompleted);assertFalse(b.isCompleted)
            assertFalse(shadowOf(controller.get()).isStoppedBySelf)
            assertTrue(ShadowPowerManager.getLatestWakeLock().isHeld)
            second.complete(Unit);shadowOf(Looper.getMainLooper()).idle()
            assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        }finally{a.cancel();b.cancel();controller.destroy();shadowOf(Looper.getMainLooper()).idle()}
        assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
    }
    @Test @Config(sdk=[35]) fun systemTimeoutStopsTheServiceAndCancelsActiveWorkWithResumablePause() {
        val app=ApplicationProvider.getApplicationContext<Application>()
        var pause:Throwable?=null
        val job=DocumentDownloadService.prepare(app,"book.epub"){
            try{awaitCancellation()}catch(e:kotlinx.coroutines.CancellationException){pause=e;throw e}
        }.apply{start()}
        val controller=Robolectric.buildService(DocumentDownloadService::class.java).create().startCommand(0,1)
        try {
            assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,controller.get().foregroundServiceType)
            shadowOf(Looper.getMainLooper()).idle()
            controller.get().onTimeout(1,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(job.isCancelled)
            assertTrue(pause is dev.grove.reader.data.DownloadPausedException)
            assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        }finally{job.cancel();controller.destroy();shadowOf(Looper.getMainLooper()).idle()}
        assertFalse(ShadowPowerManager.getLatestWakeLock().isHeld)
    }
}
