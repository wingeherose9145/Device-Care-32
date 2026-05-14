package com.system.helper

import android.content.pm.ActivityInfo
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class PlayerActivity : AppCompatActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var seekBar: SeekBar

    private lateinit var videoUris: ArrayList<String>

    private var currentIndex = 0

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var gestureDetector: GestureDetector

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContentView(R.layout.activity_player)

        playerView = findViewById(R.id.playerView)
        seekBar = findViewById(R.id.seekBar)

        player = ExoPlayer.Builder(this).build()

        playerView.player = player
        playerView.useController = false

        videoUris =
            intent.getStringArrayListExtra("video_list")
                ?: arrayListOf()

        currentIndex =
            intent.getIntExtra("current_index", 0)

        if (videoUris.isEmpty()) {

            Toast.makeText(
                this,
                "没有视频可播放",
                Toast.LENGTH_SHORT
            ).show()

            finish()
            return
        }

        setupGestureDetector()

        setupSeekBar()

        player.addListener(object : Player.Listener {

            override fun onPlaybackStateChanged(state: Int) {

                if (state == Player.STATE_ENDED) {
                    playNextVideo()
                }
            }
        })

        playCurrentVideo()

        playerView.setOnClickListener {

            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    private fun playCurrentVideo() {

        try {

            val uri = Uri.parse(videoUris[currentIndex])

            // 提前读取视频方向
            setVideoOrientation(uri)

            player.stop()

            player.setMediaItem(
                MediaItem.fromUri(uri)
            )

            player.prepare()

            player.play()

        } catch (e: Exception) {

            Toast.makeText(
                this,
                "播放失败",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // 关键优化：播放前提前设置方向
    private fun setVideoOrientation(uri: Uri) {

        try {

            val retriever = MediaMetadataRetriever()

            retriever.setDataSource(this, uri)

            val width =
                retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
                )?.toIntOrNull() ?: 0

            val height =
                retriever.extractMetadata(
                    MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
                )?.toIntOrNull() ?: 0

            requestedOrientation =
                if (height > width) {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }

            retriever.release()

        } catch (e: Exception) {

            requestedOrientation =
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    private fun playNextVideo() {

        if (currentIndex < videoUris.size - 1) {

            currentIndex++

            playCurrentVideo()
        }
    }

    private fun playPreviousVideo() {

        if (currentIndex > 0) {

            currentIndex--

            playCurrentVideo()
        }
    }

    private fun setupGestureDetector() {

        gestureDetector =
            GestureDetector(
                this,
                object : GestureDetector.SimpleOnGestureListener() {

                    override fun onFling(
                        e1: MotionEvent?,
                        e2: MotionEvent,
                        velocityX: Float,
                        velocityY: Float
                    ): Boolean {

                        if (kotlin.math.abs(velocityX) > 700) {

                            if (velocityX > 0) {
                                playPreviousVideo()
                            } else {
                                playNextVideo()
                            }

                            return true
                        }

                        return false
                    }
                }
            )

        playerView.setOnTouchListener { _, event ->

            gestureDetector.onTouchEvent(event)

            false
        }
    }

    private fun setupSeekBar() {

        handler.post(object : Runnable {

            override fun run() {

                if (player.duration > 0) {

                    seekBar.max =
                        player.duration.toInt()

                    seekBar.progress =
                        player.currentPosition.toInt()
                }

                handler.postDelayed(this, 500)
            }
        })

        seekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {

                override fun onProgressChanged(
                    seekBar: SeekBar?,
                    progress: Int,
                    fromUser: Boolean
                ) {

                    if (fromUser) {
                        player.seekTo(progress.toLong())
                    }
                }

                override fun onStartTrackingTouch(
                    seekBar: SeekBar?
                ) {
                }

                override fun onStopTrackingTouch(
                    seekBar: SeekBar?
                ) {
                }
            }
        )
    }

    override fun onPause() {
        super.onPause()
        player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()

        handler.removeCallbacksAndMessages(null)

        player.release()
    }
}
