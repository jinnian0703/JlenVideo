package top.jlen.vod.offline

import android.app.Activity
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.*
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.FileDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import top.jlen.vod.data.PlaybackResumeRecord
import top.jlen.vod.data.PlaybackResumeStore
import java.io.File

class OfflinePlayerActivity : Activity() {
    private lateinit var row: Entry
    private lateinit var video: File
    private lateinit var view: PlayerView
    private val resumes by lazy { PlaybackResumeStore(this) }
    private var player: ExoPlayer? = null
    private var wasPlaying = true
    private val handler = Handler(Looper.getMainLooper())
    private val checkpoint = object : Runnable { override fun run() { savePosition(); handler.postDelayed(this, 5000) } }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val library = OfflineLibrary.get(this)
        val item = library.entries.value.firstOrNull { it.id == intent.getStringExtra("id") && it.stage == Stage.DONE }
        if(item == null) { fail("该下载已被删除"); return }
        row = item; video = File(File(library.root,row.id),row.file)
        if (!video.isFile) { fail("离线文件不存在，请删除后重新下载"); return }
        title = "${row.title} · ${row.episode}"
        view = PlayerView(this)
        val back = Button(this).apply { text = "‹ 返回"; setOnClickListener { finish() } }
        view.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { back.visibility = it })
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK); addView(view,FrameLayout.LayoutParams(MATCH_PARENT,MATCH_PARENT))
            addView(back,FrameLayout.LayoutParams(WRAP_CONTENT,WRAP_CONTENT,Gravity.TOP or Gravity.START))
        })
    }
    override fun onStart() {
        super.onStart(); if(isFinishing) return
        val resume = resumes.loadForSource(row.vod,row.source,row.si)?.takeIf { it.episodeIndex == row.ei }
        // Deliberately file-only: even an unexpected remote reference cannot open a socket.
        val p = ExoPlayer.Builder(this).setMediaSourceFactory(DefaultMediaSourceFactory(FileDataSource.Factory())).build()
        player = p; view.player = p
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { fail("离线文件无法播放，请删除后重新下载或更换线路") }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if(isPlaying) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        })
        p.setMediaItem(MediaItem.fromUri(Uri.fromFile(video)),resume?.positionMs ?: 0L)
        p.setPlaybackSpeed(resume?.speed ?: 1f); p.prepare(); p.playWhenReady = wasPlaying
        handler.postDelayed(checkpoint,5000)
    }
    override fun onPause() { savePosition(); super.onPause() }
    override fun onStop() {
        handler.removeCallbacks(checkpoint); savePosition()
        player?.let { wasPlaying = it.playWhenReady; view.player = null; it.release() }; player = null
        super.onStop()
    }
    private fun savePosition() {
        val p = player ?: return
        if(p.playerError != null || p.playbackState == Player.STATE_IDLE || p.duration <= 0) return
        val pos = if(p.playbackState == Player.STATE_ENDED || (p.duration > 60_000 && p.currentPosition >= p.duration - 10_000)) 0L else p.currentPosition
        resumes.save(PlaybackResumeRecord(vodId=row.vod,sourceName=row.source,sourceIndex=row.si,episodeIndex=row.ei,positionMs=pos,speed=p.playbackParameters.speed,updatedAt=System.currentTimeMillis()))
    }
    private fun fail(message: String) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); finish() }
}
