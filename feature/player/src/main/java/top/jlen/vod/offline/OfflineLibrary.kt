package top.jlen.vod.offline

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import org.json.JSONObject
import top.jlen.vod.AppConfig
import top.jlen.vod.PLAYER_DESKTOP_UA
import top.jlen.vod.data.AppleCmsRepository
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import java.util.UUID

internal enum class Stage { QUEUED, ACTIVE, PAUSED, FAILED, DONE }
internal data class Entry(val id: String, val vod: String, val title: String, val source: String, val si: Int,
    val ei: Int, val episode: String, val page: String, val created: Long, val stage: Stage = Stage.QUEUED,
    val bytes: Long = 0, val percent: Int = -1, val quality: String = "", val file: String = "", val error: String = "",
    val totalBytes: Long = -1, val bytesPerSecond: Long = 0, val poster: String = "", val folder: String = "", val single: Boolean = false) {
    fun json() = JSONObject().apply {
        put("single",single); put("poster",poster); put("folder",folder); put("vod",vod); put("title",title); put("source",source); put("si",si); put("ei",ei); put("episode",episode)
        put("page",page); put("created",created); put("stage",stage.name); put("bytes",bytes); put("percent",percent)
        put("totalBytes",totalBytes); put("quality",quality); put("file",file); put("error",error)
    }.toString()
    companion object {
        fun read(id: String, raw: String) = JSONObject(raw).run {
            Entry(id,getString("vod"),getString("title"),getString("source"),getInt("si"),getInt("ei"),getString("episode"),getString("page"),getLong("created"),
                Stage.valueOf(getString("stage")).let { if (it == Stage.ACTIVE) Stage.QUEUED else it },optLong("bytes"),optInt("percent",-1),optString("quality"), optString("file"),optString("error"), optLong("totalBytes", -1), poster = optString("poster"), folder = optString("folder"), single = optBoolean("single")) }
    }
}

internal class OfflineLibrary private constructor(private val app: Context) {
    private val prefs = app.getSharedPreferences("offline_library_v1",Context.MODE_PRIVATE)
    val root = File(app.noBackupFilesDir,"offline-video").apply { mkdirs() }
    val entries = MutableStateFlow(prefs.all.mapNotNull { (id,v) -> if(v is String && !id.startsWith(REMOVING)) runCatching { Entry.read(id,v) }.getOrNull() else null }.sortedBy { it.created })
    val mobile = MutableStateFlow(prefs.getBoolean("mobile",false))
    private val net = app.getSystemService(ConnectivityManager::class.java)
    private val scheduler = app.getSystemService(JobScheduler::class.java)
    val allowed = MutableStateFlow(networkAllowed())
    val parallelism = MutableStateFlow(prefs.getInt("parallelism", 2).coerceIn(1,5))
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val draining = Mutex()
    private val coverLocks = Array(16) { Mutex() }
    private val files = OfflineFiles(root)
    fun episodeDir(row: Entry) = files.episode(row.folder, row.id)
    fun cover(row: Entry) = files.cover(row.folder)
    val diskRevision = MutableStateFlow(0L)
    @Synchronized private fun diskChanged() { diskRevision.value++ }
    @Synchronized fun setParallelism(value: Int) {
        parallelism.value = value.coerceIn(1, 5)
        prefs.edit().putInt("parallelism", parallelism.value).apply()
        wake.trySend(Unit)
        schedule()
    }
    private val cleanup = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val active = mutableMapOf<String, Run>()
    private class Run(val id: String, val job: Job, val client: OkHttpClient)
    private val baseClient = OkHttpClient.Builder().addInterceptor { chain -> chain.proceed(chain.request().newBuilder()
        .header("User-Agent",PLAYER_DESKTOP_UA).header("Referer",AppConfig.appleCmsBaseUrl)
        .header("Origin",AppConfig.appleCmsOrigin).build()) }.build()
    private val repo by lazy { AppleCmsRepository(app) }
    init {
        val pending = prefs.all.mapNotNull { (key, folder) ->
            if (key.startsWith(REMOVING) && folder is String) key.removePrefix(REMOVING) to folder else null
        }
        if (pending.isNotEmpty()) cleanup.launch { deleteLocalFiles(pending) }
        net.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) = refreshNetwork()
            override fun onLost(network: Network) = refreshNetwork()
        })
    }
    private fun networkAllowed(): Boolean {
        val cap = net.getNetworkCapabilities(net.activeNetwork) ?: return false
        return cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && (mobile.value || cap.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
    }
    @Synchronized private fun refreshNetwork() {
        val now = networkAllowed(); val changed = allowed.value != now; allowed.value = now
        if (!now) interrupt() else if(changed) schedule()
    }
    @Synchronized fun setMobile(value: Boolean) {
        mobile.value = value; prefs.edit().putBoolean("mobile",value).apply(); refreshNetwork(); schedule(force = true)
    }
    @Synchronized fun add(vod: String, title: String, poster: String, source: String, si: Int, single: Boolean, episodes: List<Triple<Int,String,String>>) {
        val rows = entries.value.toMutableList(); val edit = prefs.edit()
        val folder = rows.firstOrNull { it.vod == vod && it.folder.isNotBlank() }?.folder ?: albumFolder(vod,title)
        for ((ei,name,page) in episodes) {
            if (rows.any { it.vod == vod && it.source == source && it.si == si && it.ei == ei }) continue
            val row = Entry(UUID.randomUUID().toString(),vod,title,source,si,ei,name,page,System.currentTimeMillis(), poster = poster, folder = folder, single = single)
            rows += row; edit.putString(row.id,row.json())
        }
        edit.apply(); entries.value = rows; wake.trySend(Unit); schedule()
    }
    @Synchronized private fun replace(row: Entry, persist: Boolean = true) {
        entries.value = entries.value.map { if(it.id == row.id) row else it }
        if(persist) prefs.edit().putString(row.id,row.json()).apply()
    }
    @Synchronized fun pauseOrResume(row: Entry) {
        val current = entries.value.find { it.id == row.id } ?: return
        val stage = when(current.stage) { Stage.QUEUED,Stage.ACTIVE -> Stage.PAUSED; Stage.PAUSED,Stage.FAILED -> Stage.QUEUED; Stage.DONE -> return }
        replace(current.copy(stage = stage,error = "",bytesPerSecond = 0)); if(stage == Stage.PAUSED) active[current.id]?.stop(); wake.trySend(Unit); schedule()
    }
    @Synchronized fun remove(ids: Set<String>, completed: Boolean) {
        val removed = entries.value.filter { it.id in ids && (it.stage == Stage.DONE) == completed }
        if (removed.isEmpty()) return
        entries.value = entries.value - removed.toSet()
        val pending = removed.map { it.id to it.folder }
        val edit = prefs.edit()
        pending.forEach { (id, folder) -> edit.remove(id).putString(REMOVING + id, folder) }
        edit.apply()
        val stopping = removed.mapNotNull { active[it.id] }.onEach { it.stop() }
        cleanup.launch {
            stopping.forEach { it.job.join() }
            deleteLocalFiles(pending)
        }
        wake.trySend(Unit)
    }
    // Keep a deletion marker until the bytes are gone, so process death cannot strand large files.
    private fun deleteLocalFiles(pending: List<Pair<String, String>>) {
        val edit = prefs.edit()
        for ((id, folder) in pending) {
            val dir = files.episode(folder, id)
            if (dir.exists() && !dir.deleteRecursively()) continue
            synchronized(this) {
                if (folder.isNotEmpty() && entries.value.none { it.folder == folder }) {
                    val cover = files.cover(folder)
                    if (cover.exists() && !cover.delete()) return@synchronized
                    File(root, folder).delete() // Another episode may still be finishing removal.
                }
                edit.remove(REMOVING + id)
            }
        }
        edit.apply()
        diskChanged()
    }
    private fun Run.stop() { job.cancel(); client.dispatcher.cancelAll() }
    @Synchronized fun interrupt() { active.values.forEach { it.stop() }; wake.trySend(Unit) }
    fun hasQueue() = entries.value.any { it.stage == Stage.QUEUED || it.stage == Stage.ACTIVE }
    @Synchronized fun schedule(force: Boolean = false) {
        if (!hasQueue() || (!force && scheduler.getPendingJob(JOB) != null)) return
        val builder = JobInfo.Builder(JOB,ComponentName(app,OfflineJob::class.java)).setPersisted(true)
        if (Build.VERSION.SDK_INT >= 28 && !mobile.value) builder.setRequiredNetwork(NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build())
        else builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        scheduler.schedule(builder.build())
    }
    private fun verifyMp4(file: File): String {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            if (extractor.drmInitData != null) throw OfflineProblem("此 MP4 需要授权播放，无法离线")
            val video = (0 until extractor.trackCount).map(extractor::getTrackFormat).firstOrNull { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true }
                ?: throw OfflineProblem("文件没有可播放的视频轨道")
            return if(video.containsKey(MediaFormat.KEY_HEIGHT)) "${video.getInteger(MediaFormat.KEY_HEIGHT)}p" else "原画质"
        } finally { extractor.release() }
    }
    suspend fun drain() = draining.withLock {
        runDownloadQueue(wake, { parallelism.value }, {
            if (networkAllowed()) entries.value.filter { it.stage == Stage.QUEUED } else emptyList()
        }, Entry::id, ::runOne)
    }

    private suspend fun saveCover(row: Entry, client: OkHttpClient) {
        if (row.poster.isBlank() || row.folder.isBlank()) return
        coverLocks[(row.folder.hashCode() and Int.MAX_VALUE) % coverLocks.size].withLock {
            val target = cover(row)
            if (target.isFile) return
            try {
                if (files.fetchCover(client, row.poster, target)) diskChanged()
            } catch (_: IOException) { /* A missing poster must not prevent offline playback. */ }
        }
    }
    private suspend fun runOne(row: Entry) {
        val job = currentCoroutineContext()[Job]!!
        val client = baseClient.newBuilder().dispatcher(okhttp3.Dispatcher()).build()
        val run = Run(row.id,job,client)
        synchronized(this) {
            if (entries.value.find { it.id == row.id }?.stage != Stage.QUEUED || !networkAllowed()) return
            active[row.id] = run; replace(row.copy(stage = Stage.ACTIVE)); diskChanged()
        }
        try {
            saveCover(row,client); job.ensureActive()
            val media = repo.resolvePlayUrl(row.page); job.ensureActive()
            if (media.useWebPlayer) throw OfflineProblem("该线路只能在线播放，请更换线路")
            val result = coroutineScope {
                val latest = AtomicReference<TransferProgress?>()
                val rate = DownloadRate(SystemClock.elapsedRealtime())
                fun publishProgress(speed: Long, sample: TransferProgress) = synchronized(this@OfflineLibrary) {
                    if (job.isActive && active[row.id] === run) replace(row.copy(stage = Stage.ACTIVE, bytes = sample.bytes,
                        totalBytes = sample.totalBytes, percent = sample.percent, quality = sample.quality, bytesPerSecond = speed), false)
                }
                val ticker = launch(Dispatchers.Default) {
                    while (isActive) {
                        delay(750)
                        val sample = latest.get()
                        val speed = rate.sample(sample?.receivedBytes ?: 0, SystemClock.elapsedRealtime())
                        if (sample != null) publishProgress(speed, sample)
                    }
                }
                try {
                    OfflineTransfer(client, episodeDir(row), { job.ensureActive() }, latest::set).download(media.url)
                } finally {
                    ticker.cancelAndJoin()
                    latest.get()?.let { publishProgress(0, it) }
                }
            }
            val quality = if(result.file.extension == "mp4") verifyMp4(result.file) else result.quality
            val size = result.file.parentFile!!.listFiles()?.sumOf { it.length() } ?: result.file.length()
            synchronized(this) { job.ensureActive(); replace(row.copy(stage=Stage.DONE,bytes=size,totalBytes=size,bytesPerSecond=0,percent=100,quality=quality,file=result.file.name,error="")) }
        } catch(e: Exception) {
            synchronized(this) { val current = entries.value.find { it.id == row.id }
                if(current?.stage == Stage.ACTIVE) {
                    if(!job.isActive || !networkAllowed()) replace(current.copy(stage=Stage.QUEUED, bytesPerSecond=0))
                    else replace(current.copy(stage=Stage.FAILED,bytesPerSecond=0,error=when {
                        e is OfflineProblem -> e.message.orEmpty()
                        e.message.orEmpty().contains("ENOSPC",true) -> "空间不足，请释放空间后重试"
                        e is IOException -> "读取片源失败，请检查网络后重试"
                        else -> "无法保存此片源，请重试或更换线路"
                    }))
                }
            }
        } finally { synchronized(this) { if(active[row.id] === run) active.remove(row.id); diskChanged() } }
    }
    companion object {
        private const val REMOVING = "removing:"
        private const val JOB = 0x4a4c44
        @Volatile private var instance: OfflineLibrary? = null
        fun get(context: Context): OfflineLibrary = instance ?: synchronized(this) { instance ?: OfflineLibrary(context.applicationContext).also { instance = it } }
    }
}

class OfflineJob : JobService() {
    private var worker: Job? = null
    override fun onStartJob(params: JobParameters): Boolean {
        val lib = OfflineLibrary.get(this)
        worker = CoroutineScope(Dispatchers.Main.immediate).launch {
            withContext(Dispatchers.IO) { lib.drain() }
            jobFinished(params,lib.hasQueue())
        }; return true
    }
    override fun onStopJob(params: JobParameters): Boolean {
        worker?.cancel()
        val lib = OfflineLibrary.get(this)
        lib.interrupt()
        return lib.hasQueue()
    }
}
