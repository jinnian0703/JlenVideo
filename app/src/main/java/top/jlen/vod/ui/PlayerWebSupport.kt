package top.jlen.vod.ui

import android.content.Context
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import top.jlen.vod.BuildConfig

internal const val PLAYER_DESKTOP_UA =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36"

internal fun createNativePlayer(
    context: Context,
    url: String,
    snapshot: PlaybackSnapshot = PlaybackSnapshot()
): ExoPlayer {
    val httpFactory = DefaultHttpDataSource.Factory()
        .setUserAgent(PLAYER_DESKTOP_UA)
        .setAllowCrossProtocolRedirects(true)
        .setDefaultRequestProperties(
            mapOf(
                "Referer" to BuildConfig.APPLE_CMS_BASE_URL,
                "Origin" to BuildConfig.APPLE_CMS_BASE_URL.trimEnd('/')
            )
        )

    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(httpFactory))
        .build()
        .apply {
            setMediaItem(MediaItem.fromUri(url))
            playbackParameters = androidx.media3.common.PlaybackParameters(snapshot.speed)
            prepare()
            if (snapshot.positionMs > 0L) {
                seekTo(snapshot.positionMs)
            }
            playWhenReady = snapshot.playWhenReady
        }
}

internal fun configurePlayerWebSettings(settings: WebSettings) {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.mediaPlaybackRequiresUserGesture = false
    settings.loadsImagesAutomatically = true
    settings.cacheMode = WebSettings.LOAD_DEFAULT
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    settings.builtInZoomControls = false
    settings.displayZoomControls = false
    settings.userAgentString = PLAYER_DESKTOP_UA
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        settings.safeBrowsingEnabled = false
    }
}

internal fun tuneEmbeddedPlayerPage(webView: WebView) {
    webView.evaluateJavascript(EMBEDDED_PLAYER_SCRIPT, null)
    webView.postDelayed({ webView.evaluateJavascript(EMBEDDED_PLAYER_SCRIPT, null) }, 900)
}

internal fun tuneFullscreenPlayerPage(webView: WebView) {
    webView.evaluateJavascript(FULLSCREEN_PLAYER_SCRIPT, null)
    webView.evaluateJavascript(TRY_FULLSCREEN_SCRIPT, null)
    webView.postDelayed({
        webView.evaluateJavascript(FULLSCREEN_PLAYER_SCRIPT, null)
        webView.evaluateJavascript(TRY_FULLSCREEN_SCRIPT, null)
    }, 800)
    webView.postDelayed({
        webView.evaluateJavascript(FULLSCREEN_PLAYER_SCRIPT, null)
        webView.evaluateJavascript(TRY_FULLSCREEN_SCRIPT, null)
    }, 1800)
    webView.postDelayed({
        webView.evaluateJavascript(FULLSCREEN_PLAYER_SCRIPT, null)
        webView.evaluateJavascript(TRY_FULLSCREEN_SCRIPT, null)
    }, 3000)
}

private const val EMBEDDED_PLAYER_SCRIPT = """
(function() {
  try {
    var style = document.getElementById('jlen-embedded-player-style');
    if (!style) {
      style = document.createElement('style');
      style.id = 'jlen-embedded-player-style';
      document.head.appendChild(style);
    }
    style.innerHTML = `
      html, body { margin: 0 !important; padding: 0 !important; background: #000 !important; }
      .header, .bottom, .fixedbar-fixed-bar, .playlist-box, .vod-right, .banner-layout-box,
      .ewave-banner-box, .links-box, .art-box, .index-ranking, .search, .nav, .a3,
      .clist-box, .head-placeholder, .banner, .bread-crumb, .friendlink, .bottom-placeholder,
      .ewave-player-footer, .player-info, .vod-info, .vod-detail, .vod-content,
      .recommend, .recommend-box, .copylink, .hot-search-list {
        display: none !important;
      }
      .container, .player-container, .vod-player, .vod-left, .video, .ewave-player-box,
      .ewave-player-fixed, .embed-responsive {
        width: 100% !important;
        max-width: none !important;
        margin: 0 !important;
        padding: 0 !important;
        background: #000 !important;
      }
      .ewave-player-fixed {
        position: relative !important;
        inset: auto !important;
      }
      .embed-responsive {
        min-height: 220px !important;
        background: #000 !important;
      }
      video, iframe {
        width: 100% !important;
        height: 100% !important;
        background: #000 !important;
        object-fit: contain !important;
      }
    `;
    window.scrollTo(0, 0);
  } catch (e) {}
})();
"""

private const val FULLSCREEN_PLAYER_SCRIPT = """
(function() {
  try {
    var style = document.getElementById('jlen-fullscreen-player-style');
    if (!style) {
      style = document.createElement('style');
      style.id = 'jlen-fullscreen-player-style';
      document.head.appendChild(style);
    }
    style.innerHTML = `
      html, body {
        margin: 0 !important;
        padding: 0 !important;
        width: 100% !important;
        height: 100% !important;
        overflow: hidden !important;
        background: #000 !important;
      }
      body > * {
        display: none !important;
      }
      .jlen-player-host {
        display: block !important;
        position: fixed !important;
        inset: 0 !important;
        z-index: 2147483647 !important;
        margin: 0 !important;
        padding: 0 !important;
        width: 100vw !important;
        height: 100vh !important;
        background: #000 !important;
      }
      .jlen-player-host .container, .jlen-player-host .player-container, .jlen-player-host .vod-player,
      .jlen-player-host .vod-left, .jlen-player-host .video, .jlen-player-host .ewave-player-box,
      .jlen-player-host .ewave-player-fixed, .jlen-player-host .embed-responsive {
        display: block !important;
        width: 100% !important;
        height: 100% !important;
        max-width: none !important;
        margin: 0 !important;
        padding: 0 !important;
        background: #000 !important;
      }
      .jlen-player-host .vod-right, .jlen-player-host .playlist-box, .jlen-player-host .ewave-player-footer,
      .jlen-player-host .player-info, .jlen-player-host .vod-info, .jlen-player-host .vod-detail,
      .jlen-player-host .recommend-box, .jlen-player-host .banner-layout-box, .jlen-player-host .copylink {
        display: none !important;
      }
      .jlen-player-host .embed-responsive {
        min-height: 100vh !important;
      }
      .jlen-player-host video, .jlen-player-host iframe {
        width: 100% !important;
        height: 100% !important;
        background: #000 !important;
        object-fit: contain !important;
      }
    `;
    var host = document.querySelector('.player-container') ||
      document.querySelector('.vod-player') ||
      document.querySelector('.ewave-player-box');
    if (host) {
      host.classList.add('jlen-player-host');
      var topNode = host;
      while (topNode && topNode.parentElement && topNode.parentElement !== document.body) {
        topNode = topNode.parentElement;
      }
      Array.prototype.forEach.call(document.body.children, function(child) {
        if (child !== topNode && child.tagName !== 'SCRIPT' && child.tagName !== 'STYLE') {
          child.style.display = 'none';
        }
      });
    }
    var close = document.querySelector('.ewave-player-fixed-close');
    if (close) {
      close.style.display = 'none';
    }
    window.scrollTo(0, 0);
  } catch (e) {}
})();
"""

private const val TRY_FULLSCREEN_SCRIPT = """
(function() {
  try {
    var items = Array.prototype.slice.call(document.querySelectorAll('button, a, div, span, i'));
    var trigger = items.find(function(node) {
      var text = ((node.innerText || '') + ' ' + (node.title || '') + ' ' + (node.getAttribute('aria-label') || '')).toLowerCase();
      return text.indexOf('全屏') >= 0 || text.indexOf('fullscreen') >= 0;
    });
    if (trigger && trigger.click) {
      trigger.click();
    }
    var video = document.querySelector('video');
    if (video) {
      if (video.play) {
        var playing = video.play();
        if (playing && playing.catch) {
          playing.catch(function() {});
        }
      }
      var requestFullscreen = video.requestFullscreen ||
        video.webkitRequestFullscreen ||
        video.webkitEnterFullscreen ||
        video.mozRequestFullScreen ||
        video.msRequestFullscreen;
      if (requestFullscreen) {
        try { requestFullscreen.call(video); } catch (e) {}
      }
    }
  } catch (e) {}
})();
"""
