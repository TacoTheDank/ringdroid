package com.ringdroid.view.widget

interface WaveformListener {
    fun waveformTouchStart(x: Float)
    fun waveformTouchMove(x: Float)
    fun waveformTouchEnd()
    fun waveformFling(x: Float)
    fun waveformDraw()
    fun waveformZoomIn()
    fun waveformZoomOut()
}