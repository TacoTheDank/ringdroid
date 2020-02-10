package com.ringdroid.view.widget

interface MarkerListener {
    fun markerTouchStart(marker: MarkerView?, pos: Float)
    fun markerTouchMove(marker: MarkerView?, pos: Float)
    fun markerTouchEnd(marker: MarkerView?)
    fun markerFocus(marker: MarkerView?)
    fun markerLeft(marker: MarkerView?, velocity: Int)
    fun markerRight(marker: MarkerView?, velocity: Int)
    fun markerEnter(marker: MarkerView?)
    fun markerKeyUp()
    fun markerDraw()
}