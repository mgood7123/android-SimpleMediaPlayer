/*
 * Copyright 2017 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.mediaplayersample

import android.os.Bundle
import android.support.v4.app.FragmentActivity
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.github.derlio.waveform.soundfile.SoundFile
import java.io.*
import java.nio.file.Path


/**
 * Allows playback of a single MP3 file via the UI. It contains a [MediaPlayerHolder]
 * which implements the [PlayerAdapter] interface that the activity uses to control
 * audio playback.
 */
class MainActivity : AppCompatActivity() {

    var AppStorage : Path? = null
    val DemoFile : String = "jazz_in_paris"
    val DemoFileExtention = ".mp3"
    var DemoFilePath : String? = null

    private var mTextDebug: TextView? = null
    private var mSeekbarAudio: SeekBar? = null
    private var mScrollContainer: ScrollView? = null
    private var mPlayerAdapter: PlayerAdapter? = null
    private var mPlaybackListner: PlaybackListener? = null
    private var mUserIsSeeking = false

    var mPlayButton : Button? = null
    var mLoopButton : Button? = null
    var mResetButton : Button? = null
    var mBGAButton : Button? = null


    private val loop_default = false
    private val background_audio_default = true
    private var loop = loop_default
    private var background_audio = background_audio_default

    fun init_waveform(file : String) {
        val soundFile = SoundFile.create(file, object : SoundFile.ProgressListener {

            internal var lastProgress = 0

            override fun reportProgress(fractionComplete: Double): Boolean {
                val progress = (fractionComplete * 100).toInt()
                if (lastProgress == progress) {
                    return true
                }
                lastProgress = progress
                Log.i(TAG, "LOAD FILE PROGRESS:$progress")

                return true
            }
        })
    }

    private fun copyAssets(OutDIR : String) {
        mPlaybackListner!!.onLogUpdated("copying assets to $OutDIR")
        val assetManager = assets
        var files: Array<String>? = null
        try {
            files = assetManager.list("")
        } catch (e: IOException) {
            mPlaybackListner!!.onLogUpdated("Failed to get asset file list. : $e")
        }

        for (filename in files!!) {
            var input : InputStream? = null
            var output : OutputStream? = null
            try {
                input = assetManager.open(filename)

                val outFile = File(OutDIR, filename)

                output = FileOutputStream(outFile)
                mPlaybackListner!!.onLogUpdated("$filename -> $OutDIR/$filename")
                copyFile(input!!, output)
                input!!.close()
                input = null
                output!!.flush()
                output!!.close()
                output = null
            } catch (e: IOException) {
//                mPlaybackListner!!.onLogUpdated("Failed to copy asset file: $filename : $e")
            }

        }
    }

    @Throws(IOException::class)
    private fun copyFile(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(1024)
        var read: Int
        read = input!!.read(buffer)
        while (read != -1) {
            output.write(buffer, 0, read)
            read = input!!.read(buffer)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initializeUI()
        initializeSeekbar()
        initializePlaybackController()
        Log.d(TAG, "onCreate: finished")
        AppStorage = getFilesDir().toPath().toAbsolutePath()
        mPlaybackListner!!.onLogUpdated("AppStorage: " + AppStorage.toString())
        copyAssets(AppStorage.toString())
    }

    override fun onStart() {
        super.onStart()
        DemoFilePath = AppStorage.toString() + "/" + DemoFile + DemoFileExtention
        mPlayerAdapter!!.loadMedia(DemoFilePath!!)
        mPlaybackListner!!.onLogUpdated("onStart: create MediaPlayer")
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations && mPlayerAdapter!!.isPlaying()) {
            mPlaybackListner!!.onLogUpdated("onStop: don't release MediaPlayer as screen is rotating & playing")
        } else {
            if (!background_audio) {
                reset()
                mPlayerAdapter!!.release()
                mPlaybackListner!!.onLogUpdated("onStop: release MediaPlayer")
            }
        }
    }

    inner class toggle {
        fun loop() {
            loop = !loop
            mLoopButton!!.text = "Loop: $loop"
            mPlaybackListner!!.onLogUpdated("Loop: $loop")
        }
        fun playback() {
            if (!mPlayerAdapter!!.isPlaying()) {
                mPlayerAdapter!!.play()
                mPlayButton!!.text = "Pause"
            } else {
                mPlayerAdapter!!.pause()
                mPlayButton!!.text = "Play"
            }
        }
        fun background_audio() {
            background_audio = !background_audio
            mBGAButton!!.text = "BGA: $background_audio"
            mPlaybackListner!!.onLogUpdated("Background Audio: $background_audio")
        }
    }
    
    fun find_buttons() {
        mPlayButton = findViewById(R.id.button_play_pause) as Button
        mLoopButton = findViewById(R.id.button_loop) as Button
        mResetButton = findViewById(R.id.button_reset) as Button
        mBGAButton = findViewById(R.id.button_background_audio) as Button
    }
    fun setup_buttons() {
        mPlayButton!!.setOnClickListener { toggle().playback() }
        mLoopButton!!.setOnClickListener { toggle().loop() }
        mResetButton!!.setOnClickListener { reset() }
        mBGAButton!!.setOnClickListener { toggle().background_audio() }
    }
    fun init_buttons() {
        mPlayButton!!.text = "Play"
        mBGAButton!!.text = "BGA: $background_audio"
        mLoopButton!!.text = "Loop: $loop"
        mResetButton!!.text = "Reset"
    }

    private fun reset() {
        mPlayerAdapter!!.reset(DemoFilePath!!)
        init_buttons()
        if (loop == !loop_default) toggle().loop()
        if (background_audio == !background_audio_default) toggle().background_audio()
    }

    private fun initializeUI() {
        mTextDebug = findViewById(R.id.text_debug) as TextView
        mSeekbarAudio = findViewById(R.id.seekbar_audio) as SeekBar
        mScrollContainer = findViewById(R.id.scroll_container) as ScrollView
        find_buttons()
        setup_buttons()
        init_buttons()
    }

    private fun initializePlaybackController() {
        init_buttons()
        mPlaybackListner = PlaybackListener()
        mPlaybackListner!!.onLogUpdated("initializePlaybackController: created MediaPlayerHolder")
        val mMediaPlayerHolder = MediaPlayerHolder(this)
        mMediaPlayerHolder.setPlaybackInfoListener(mPlaybackListner)
        mPlayerAdapter = mMediaPlayerHolder
        mPlaybackListner!!.onLogUpdated("initializePlaybackController: MediaPlayerHolder progress callback set")
    }

    private fun initializeSeekbar() {
        mSeekbarAudio!!.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                internal var userSelectedPosition = 0

                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    mUserIsSeeking = true
                }

                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        userSelectedPosition = progress
                    }
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    mUserIsSeeking = false
                    mPlayerAdapter!!.seekTo(userSelectedPosition)
                }
            })
    }

    inner class PlaybackListener : PlaybackInfoListener() {

        public override fun onDurationChanged(duration: Int) {
            mSeekbarAudio!!.max = duration
            mPlaybackListner!!.onLogUpdated("setPlaybackDuration: setMax($duration)")
        }

        public override fun onPositionChanged(position: Int) {
            if (!mUserIsSeeking) {
                mSeekbarAudio!!.setProgress(position, true)
                mPlaybackListner!!.onLogUpdated("setPlaybackPosition: setProgress($position)")
            }
        }

        public override fun onStateChanged(@State state: Int) {
            val stateToString = PlaybackInfoListener.convertStateToString(state)
            onLogUpdated("onStateChanged($stateToString)")
        }

        public override fun onPlaybackCompleted() {
            if (loop) mPlayerAdapter!!.play()
            else toggle().playback()
        }

        public override fun onLogUpdated(message: String) {
            if (mTextDebug != null) {
                mTextDebug!!.append(message)
                mTextDebug!!.append("\n")
                // Moves the scrollContainer focus to the end.
                mScrollContainer!!.post { mScrollContainer!!.fullScroll(ScrollView.FOCUS_DOWN) }
            }
        }
    }

    companion object {
        val TAG = "MainActivity"
        val MEDIA_RES_ID = R.raw.jazz_in_paris
    }
}