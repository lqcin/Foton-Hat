package com.foton.dxfhelper

/**
 * Robot Kontrol Sistemi'ne aktarılacak temel seçim verisi.
 *
 * Accessibility servisi artık kullanılmıyor; entegrasyon FOTON Klavye
 * (InputMethodService) üzerinden yapılır.
 */
data class RobotSelection(
    val sectionNo: String,
    val startManhole: String,
    val endManhole: String,
    val lengthMeters: Double
)
