package dev.grove.reader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import dev.grove.reader.data.DownloadPausedException
import dev.grove.reader.domain.FileDownloadProgress
import kotlinx.coroutines.*
import java.util.UUID

/** Runs user-started PDF/EPUB transfers independently of the Activity and keeps the CPU awake. */
class DocumentDownloadService : Service() {
    companion object {
        private const val CHANNEL="file_downloads"
        private const val NOTIFICATION=42
        private const val WAKE_TIMEOUT=6*60*60*1000L
        private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        private val main=Handler(Looper.getMainLooper())
        private data class Transfer(val name:String,val job:Job,var progress:FileDownloadProgress?=null)
        // Accessed only on the main thread, including completion callbacks.
        private val transfers=linkedMapOf<String,Transfer>()
        private var instance:DocumentDownloadService?=null

        /** Register before starting the lazy job, while the initiating Activity is visible. */
        fun prepare(context:Context,name:String,block:suspend CoroutineScope.(String)->Unit):Job {
            val app=context.applicationContext
            val serviceIntent=Intent(app,DocumentDownloadService::class.java)
            val id=UUID.randomUUID().toString()
            val job=scope.launch(start=CoroutineStart.LAZY){block(id)}
            transfers[id]=Transfer(name,job)
            job.invokeOnCompletion {
                main.post {
                    transfers.remove(id)
                    // If startup is still queued, let onStartCommand promote and stop the
                    // service. Stopping an unpromoted FGS can crash on fast cache hits.
                    instance?.let{if(transfers.isEmpty())it.stopSelf()else it.refresh()}
                }
            }
            try {
                app.startForegroundService(serviceIntent)
                instance?.refresh()
            }catch(e:Exception){transfers.remove(id);job.cancel();throw e}
            return job
        }

        fun progress(id:String,progress:FileDownloadProgress) {
            transfers[id]?.progress=progress
            instance?.let{if(SystemClock.elapsedRealtime()-it.lastNotification>=1000)it.refresh()}
        }
    }

    private var wakeLock:PowerManager.WakeLock?=null
    private var lastNotification=0L
    override fun onCreate() {
        super.onCreate();instance=this
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL,"文件加载",NotificationManager.IMPORTANCE_LOW))
        wakeLock=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"Grove:FileDownload").apply{
            setReferenceCounted(false);acquire(WAKE_TIMEOUT)
        }
        refresh()
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        // Even a transfer that finished before onStartCommand must satisfy the FGS contract.
        refresh()
        if(transfers.isEmpty())stopSelf(startId)
        return START_NOT_STICKY
    }
    private fun notification():Notification {
        val transfer=transfers.values.firstOrNull()
        val progress=transfer?.progress
        val percent=progress?.fraction?.let{(it*100).toInt().coerceIn(0,100)}
        val content=listOfNotNull(transfer?.name,percent?.let{"$it%"},
            transfers.size.takeIf{it>1}?.let{"共 $it 个文件"}).joinToString(" · ")
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this,CHANNEL).setSmallIcon(R.drawable.ic_download)
            .setContentTitle("文件加载中").setContentText(content).setContentIntent(open)
            .setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_PROGRESS)
            .setProgress(100,percent?:0,percent==null).build()
    }
    private fun refresh() {
        lastNotification=SystemClock.elapsedRealtime()
        // FGS notifications are allowed even when notification permission is denied; the task
        // then appears in Android's active-apps UI. Downloads never depend on that permission.
        val notification=notification()
        if(Build.VERSION.SDK_INT>=29)startForeground(NOTIFICATION,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(NOTIFICATION,notification)
    }

    override fun onTimeout(startId:Int,fgsType:Int) {
        // Android 15's dataSync time budget is finite. Keep partials so returning can resume.
        pauseTransfers();stopForeground(STOP_FOREGROUND_REMOVE);stopSelf()
    }
    private fun pauseTransfers(){transfers.values.toList().forEach{it.job.cancel(DownloadPausedException())}}
    override fun onDestroy() {
        if(instance===this){instance=null;pauseTransfers()}
        wakeLock?.let{if(it.isHeld)it.release()};wakeLock=null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent:Intent?):IBinder?=null
}
