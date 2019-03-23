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

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaPlayer

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Exposes the functionality of the [MediaPlayer] and implements the [PlayerAdapter]
 * so that [MainActivity] can control music playback.
 */
class MediaPlayerHolder(context: Context) : PlayerAdapter {

    private val mContext: Context
    private var mMediaPlayer: MediaPlayer? = null
    private var mResourceId: Int = 0
    private var mCurrentPlayingFile: String? = null
    private var mPlaybackInfoListener: PlaybackInfoListener? = null
    private var mExecutor: ScheduledExecutorService? = null
    private var mSeekbarPositionUpdateTask: Runnable? = null

    init {
        mContext = context.applicationContext
    }

    /**
     * Once the [MediaPlayer] is released, it can't be used again, and another one has to be
     * created. In the onStop() method of the [MainActivity] the [MediaPlayer] is
     * released. Then in the onStart() of the [MainActivity] a new [MediaPlayer]
     * object has to be created. That's why this method is private, and called by load(int) and
     * not the constructor.
     */
    private fun initializeMediaPlayer() {
        if (mMediaPlayer == null) {
            mMediaPlayer = MediaPlayer()
            mMediaPlayer!!.setOnCompletionListener {
                stopUpdatingCallbackWithPosition(true)
                logToUI("MediaPlayer playback completed")
                if (mPlaybackInfoListener != null) {
                    mPlaybackInfoListener!!.onStateChanged(PlaybackInfoListener.State.COMPLETED)
                    mPlaybackInfoListener!!.onPlaybackCompleted()
                }
            }
            logToUI("mMediaPlayer = new MediaPlayer()")
        }
    }

    fun setPlaybackInfoListener(listener: PlaybackInfoListener?) {
        mPlaybackInfoListener = listener
    }

    // Implements PlaybackControl.
    override fun loadMedia(resourceId: Int) {
        mResourceId = resourceId

        initializeMediaPlayer()

        val assetFileDescriptor = mContext.resources.openRawResourceFd(mResourceId)
        try {
            logToUI("load() {1. setDataSource}")
            mMediaPlayer!!.setDataSource(assetFileDescriptor)
        } catch (e: Exception) {
            logToUI(e.toString())
        }

        try {
            logToUI("load() {2. prepare}")
            mMediaPlayer!!.prepare()
        } catch (e: Exception) {
            logToUI(e.toString())
        }

        initializeProgressCallback()
        logToUI("initializeProgressCallback()")
    }

    // Implements PlaybackControl.
    override fun loadMedia(file : String) {

        initializeMediaPlayer()

        try {
            logToUI("load() {1. setDataSource}")
            logToUI("file to load is $file")
            mCurrentPlayingFile = file
            mMediaPlayer!!.setDataSource(file)
        } catch (e: Exception) {
            logToUI(e.toString())
        }

        try {
            logToUI("load() {2. prepare}")
            mMediaPlayer!!.prepare()
        } catch (e: Exception) {
            logToUI(e.toString())
        }

        initializeProgressCallback()
        logToUI("initializeProgressCallback()")
    }

    override fun release() {
        if (mMediaPlayer != null) {
            logToUI("release() and mMediaPlayer = null")
            mMediaPlayer!!.release()
            mMediaPlayer = null
        }
    }

    override fun isPlaying(): Boolean {
        return if (mMediaPlayer != null) {
            mMediaPlayer!!.isPlaying
        } else false
    }

    override fun play() {
        if (mMediaPlayer != null && !mMediaPlayer!!.isPlaying) {
            logToUI("playbackStart() $mCurrentPlayingFile")
            mMediaPlayer!!.start()
            if (mPlaybackInfoListener != null) {
                mPlaybackInfoListener!!.onStateChanged(PlaybackInfoListener.State.PLAYING)
            }
            startUpdatingCallbackWithPosition()
        }
    }

    override fun reset(file : String) {
        if (mMediaPlayer != null) {
            logToUI("playbackReset()")
            mMediaPlayer!!.reset()
            loadMedia(file)
            if (mPlaybackInfoListener != null) {
                mPlaybackInfoListener!!.onStateChanged(PlaybackInfoListener.State.RESET)
            }
            stopUpdatingCallbackWithPosition(true)
        }
    }

    override fun pause() {
        if (mMediaPlayer != null && mMediaPlayer!!.isPlaying) {
            mMediaPlayer!!.pause()
            if (mPlaybackInfoListener != null) {
                mPlaybackInfoListener!!.onStateChanged(PlaybackInfoListener.State.PAUSED)
            }
            logToUI("playbackPause()")
        }
    }

    override fun seekTo(position: Int) {
        if (mMediaPlayer != null) {
            logToUI("seekTo() " + position.toString() + " ms")
            mMediaPlayer!!.seekTo(position)
        }
    }

    /**
     * Syncs the mMediaPlayer position with mPlaybackProgressCallback via recurring task.
     */
    private fun startUpdatingCallbackWithPosition() {
        if (mExecutor == null) {
            mExecutor = Executors.newSingleThreadScheduledExecutor()
        }
        if (mSeekbarPositionUpdateTask == null) {
            mSeekbarPositionUpdateTask = Runnable { updateProgressCallbackTask() }
        }
        mExecutor!!.scheduleAtFixedRate(
            mSeekbarPositionUpdateTask,
            0,
            PLAYBACK_POSITION_REFRESH_INTERVAL_MS.toLong(),
            TimeUnit.MILLISECONDS
        )
    }

    // Reports media playback position to mPlaybackProgressCallback.
    private fun stopUpdatingCallbackWithPosition(resetUIPlaybackPosition: Boolean) {
        if (mExecutor != null) {
            mExecutor!!.shutdownNow()
            mExecutor = null
            mSeekbarPositionUpdateTask = null
            if (resetUIPlaybackPosition && mPlaybackInfoListener != null) {
                mPlaybackInfoListener!!.onPositionChanged(0)
            }
        }
    }

    private fun updateProgressCallbackTask() {
        if (mMediaPlayer != null && mMediaPlayer!!.isPlaying) {
            val currentPosition = mMediaPlayer!!.currentPosition
            if (mPlaybackInfoListener != null) {
                mPlaybackInfoListener!!.onPositionChanged(currentPosition)
            }
        }
    }

    override fun initializeProgressCallback() {
        val duration = mMediaPlayer!!.duration
        if (mPlaybackInfoListener != null) {
            mPlaybackInfoListener!!.onDurationChanged(duration)
            mPlaybackInfoListener!!.onPositionChanged(0)
            logToUI("firing setPlaybackDuration(" + TimeUnit.MILLISECONDS.toSeconds(duration.toLong()).toString() + " sec)")
            logToUI("firing setPlaybackPosition(0)")
        }
    }

    private fun logToUI(message: String) {
        if (mPlaybackInfoListener != null) {
            mPlaybackInfoListener!!.onLogUpdated(message)
        }
    }

    companion object {

        val PLAYBACK_POSITION_REFRESH_INTERVAL_MS = 1000
    }

}
