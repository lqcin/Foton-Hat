package com.foton.dxfhelper

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var mapView: GeoMapView
    private lateinit var status: TextView
    private lateinit var fileLabel: TextView
    private lateinit var selectionLabel: TextView
    private lateinit var reverseButton: Button
    private lateinit var confirmButton: Button
    private lateinit var undoButton: Button
    private lateinit var countLabel: TextView
    private lateinit var locationLabel: TextView
    private lateinit var focusButton: Button
    private lateinit var routeBar: LinearLayout
    private lateinit var routeLabel: TextView
    private lateinit var routeCloseButton: Button

    private lateinit var locationManager: LocationManager
    private var lastLocation: Location? = null
    private var locationUpdatesActive = false

    private var activeUri: Uri? = null
    private var activeName: String = ""
    private var currentInfo: SelectedLineInfo? = null
    private var activeDrawing: DxfDrawing? = null
    private var completed = mutableSetOf<String>()
    private lateinit var completionStore: CompletionStore
    private var launchedFromIme: Boolean = false
    private val executor = Executors.newSingleThreadExecutor()

    private val locationPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { grants ->
            val granted =
                grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                    grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (granted) {
                startLocationUpdates()
            } else {
                updateLocationLabel(
                    "Konum izni kapalı",
                    Color.rgb(220, 130, 90)
                )
            }
        }

    private val openMap = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            getSharedPreferences("fotonmap_main", MODE_PRIVATE).edit()
                .putString("active_uri", uri.toString())
                .apply()
            loadFotonMap(uri)
        }
    }

    private val createPdf = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) exportPdf(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        completionStore = CompletionStore(this)
        locationManager =
            getSystemService(
                Context.LOCATION_SERVICE
            ) as LocationManager

        buildUi()
        handleIntent(intent)
        ensureLocationPermission()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (launchedFromIme && ImeSelectionStore.isSelectionRequestActive(this@MainActivity)) {
                    ImeSelectionStore.cancelSelectionRequest(this@MainActivity)
                }
                finish()
            }
        })

        val saved = getSharedPreferences("fotonmap_main", MODE_PRIVATE).getString("active_uri", null)
        if (saved != null) loadFotonMap(Uri.parse(saved))
        else status.text = "Önce AutoCAD'den üretilmiş .fotonmap dosyasını seç."
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()

        if (::mapView.isInitialized) {
            mapView.onResume()
        }

        if (hasLocationPermission()) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        stopLocationUpdates()

        if (::mapView.isInitialized) {
            mapView.onPause()
        }

        super.onPause()
    }

    override fun onDestroy() {
        stopLocationUpdates()
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun handleIntent(intent: Intent?) {
        val requestedByIntent = intent?.getBooleanExtra(EXTRA_FROM_IME, false) == true
        val requestedByStore = ImeSelectionStore.isSelectionRequestActive(this)

        launchedFromIme = requestedByIntent || requestedByStore
        if (launchedFromIme) {
            status.text = "FOTON Klavye için hat seçimi. Hattı seç ve ONAYLA; seçim klavyenin üstündeki Son Seçilen Hat kartına kaydolacak."
        }
    }

    private fun buildUi() {
        val root =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.VERTICAL

                setBackgroundColor(
                    Color.rgb(
                        11,
                        20,
                        32
                    )
                )

                setPadding(
                    dp(6),
                    dp(4),
                    dp(6),
                    dp(4)
                )
            }

        val toolScroll =
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled =
                    false
                isFillViewport =
                    true
            }

        val tools =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL
                gravity =
                    Gravity.CENTER_VERTICAL
            }

        tools.addView(
            button("FOTONMAP") {
                openMap.launch(
                    arrayOf("*/*")
                )
            }
        )

        tools.addView(
            button("PDF") {
                requestPdf()
            }
        )

        tools.addView(
            button("KLAVYE") {
                (
                    getSystemService(
                        Context.INPUT_METHOD_SERVICE
                    ) as InputMethodManager
                    )
                    .showInputMethodPicker()
            }
        )

        tools.addView(
            button("−") {
                mapView.zoomOut()
            }
        )

        tools.addView(
            button("+") {
                mapView.zoomIn()
            }
        )

        tools.addView(
            button("SIĞDIR") {
                mapView.resetView()
            }
        )

        focusButton =
            button("◎ FOKUS") {
                focusOnCurrentLocation()
            }.apply {
                isEnabled =
                    false
            }

        tools.addView(
            focusButton
        )

        toolScroll.addView(
            tools
        )

        root.addView(
            toolScroll,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
            )
        )

        val infoRow =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL
                gravity =
                    Gravity.CENTER_VERTICAL
                setPadding(
                    dp(4),
                    0,
                    dp(4),
                    0
                )
            }

        fileLabel =
            TextView(this).apply {
                setTextColor(
                    Color.WHITE
                )
                textSize =
                    12f
                text =
                    "FOTONMAP seçilmedi"
                maxLines =
                    1
            }

        infoRow.addView(
            fileLabel,
            LinearLayout.LayoutParams(
                0,
                dp(30),
                1f
            )
        )

        countLabel =
            TextView(this).apply {
                setTextColor(
                    Color.rgb(
                        80,
                        220,
                        140
                    )
                )
                textSize =
                    12f
                gravity =
                    Gravity.CENTER_VERTICAL
                text =
                    "0 / 0"
            }

        infoRow.addView(
            countLabel
        )

        locationLabel =
            TextView(this).apply {
                setTextColor(
                    Color.rgb(
                        160,
                        180,
                        200
                    )
                )
                textSize =
                    12f
                setPadding(
                    dp(10),
                    0,
                    0,
                    0
                )
                gravity =
                    Gravity.CENTER_VERTICAL
                text =
                    "GPS bekleniyor"
            }

        infoRow.addView(
            locationLabel
        )

        root.addView(
            infoRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(30)
            )
        )

        val mapContainer =
            FrameLayout(this)

        mapView =
            GeoMapView(this).apply {
                onSegmentSelected =
                    {
                        segment,
                        info ->

                        currentInfo =
                            info

                        routeTarget =
                            segment

                        refreshSelection()
                        refreshRouteGuide()

                        // Uzakta test ediliyorsa rehber çizgisi de görünür olsun.
                        if (
                            routeDistanceMeters()
                                ?.let {
                                    it > 250.0
                                } == true
                        ) {
                            showRouteOverview()
                        }
                    }
            }

        mapContainer.addView(
            mapView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        status =
            TextView(this).apply {
                setTextColor(
                    Color.rgb(
                        225,
                        235,
                        245
                    )
                )
                setBackgroundColor(
                    Color.argb(
                        175,
                        15,
                        26,
                        40
                    )
                )
                textSize =
                    12f
                setPadding(
                    dp(8),
                    dp(4),
                    dp(8),
                    dp(4)
                )
                maxLines =
                    2
            }

        mapContainer.addView(
            status,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        )

        routeBar =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL
                gravity =
                    Gravity.CENTER_VERTICAL
                visibility =
                    View.GONE
                setPadding(
                    dp(8),
                    dp(4),
                    dp(5),
                    dp(4)
                )
                setBackgroundColor(
                    Color.argb(
                        225,
                        42,
                        31,
                        56
                    )
                )
            }

        routeLabel =
            TextView(this).apply {
                setTextColor(
                    Color.rgb(
                        230,
                        210,
                        255
                    )
                )
                textSize =
                    13f
                text =
                    "Hedef yok"
            }

        routeBar.addView(
            routeLabel,
            LinearLayout.LayoutParams(
                0,
                dp(38),
                1f
            )
        )

        routeBar.addView(
            button("HEDEF") {
                mapView.focusOnTarget()
            }
        )

        routeBar.addView(
            button("İKİSİ") {
                mapView.showRouteOverview()
            }
        )

        routeCloseButton =
            button("KAPAT") {
                closeRouteGuide()
            }

        routeBar.addView(
            routeCloseButton
        )

        mapContainer.addView(
            routeBar,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ).apply {
                topMargin =
                    dp(50)
                leftMargin =
                    dp(6)
                rightMargin =
                    dp(6)
            }
        )

        val bottom =
            LinearLayout(this).apply {
                orientation =
                    LinearLayout.HORIZONTAL
                gravity =
                    Gravity.CENTER_VERTICAL
                setPadding(
                    dp(8),
                    dp(4),
                    dp(5),
                    dp(4)
                )
                setBackgroundColor(
                    Color.argb(
                        220,
                        11,
                        20,
                        32
                    )
                )
            }

        selectionLabel =
            TextView(this).apply {
                setTextColor(
                    Color.WHITE
                )
                textSize =
                    14f
                text =
                    "Bir hatta dokun"
            }

        bottom.addView(
            selectionLabel,
            LinearLayout.LayoutParams(
                0,
                dp(40),
                1f
            )
        )

        reverseButton =
            button("TERS") {
                currentInfo =
                    currentInfo
                        ?.reversedCopy()

                refreshSelection()
            }.apply {
                isEnabled =
                    false
            }

        undoButton =
            button("GERİ AL") {
                toggleCompleted(false)
            }.apply {
                isEnabled =
                    false
            }

        confirmButton =
            button("ONAYLA") {
                confirmSelection()
            }.apply {
                isEnabled =
                    false
            }

        bottom.addView(
            reverseButton
        )
        bottom.addView(
            undoButton
        )
        bottom.addView(
            confirmButton
        )

        mapContainer.addView(
            bottom,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        root.addView(
            mapContainer,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )

        setContentView(
            root
        )
    }

    private fun loadFotonMap(uri: Uri) {
        activeUri = uri
        activeName = displayName(uri)
        fileLabel.text = activeName
        status.text = "FOTONMAP okunuyor..."
        currentInfo = null
        activeDrawing = null
        closeRouteGuide()
        refreshSelection()

        executor.execute {
            val result = runCatching {
                val raw = FotonMapFileStore.readBestEffort(this, uri)
                FotonMapParser.parse(
                    raw.byteInputStream(Charsets.UTF_8)
                )
            }
            runOnUiThread {
                result.onSuccess { drawing ->
                    activeDrawing = drawing

                    // Yeni dosyalarda gerçek kaynak .fotonmap içindeki completed alanıdır.
                    // Eski dosyada completed yoksa eski tablet kaydını bir kez devralırız.
                    completed =
                        (
                            drawing.embeddedCompletedIds
                                ?: completionStore.load(uri)
                        ).toMutableSet()

                    // Haritada artık bulunmayan eski id'leri temizle.
                    val valid = drawing.segments.mapTo(hashSetOf()) { it.id }
                    completed.retainAll(valid)

                    // Local store yalnızca emniyet / eski sürüm fallback'i olarak kalır.
                    completionStore.save(uri, completed)

                    // Dosya sadece açıldığı için artık yeniden yazılmaz.
                    // completed ilk kez ONAYLA / İŞARETİ KALDIR işleminde
                    // aynı .fotonmap dosyasına eklenir.

                    mapView.drawing = drawing
                    mapView.completedIds = completed

                    lastLocation?.let {
                        updateLocationOverlay(it)
                    }

                    updateCount()
                    val named = drawing.segments.count { it.fromName.isNotBlank() && it.toName.isNotBlank() }
                    status.text = "${drawing.segments.size} hat yüklendi • $named hattın iki baca adı hazır • hatta dokun."
                }.onFailure { err ->
                    status.text = "FOTONMAP okunamadı: ${err.message}"
                    Toast.makeText(this, "FOTONMAP okunamadı", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val locationListener =
        LocationListener { location ->
            lastLocation = location
            updateLocationOverlay(location)
        }

    private fun hasLocationPermission(): Boolean =
        ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

    private fun ensureLocationPermission() {
        if (hasLocationPermission()) {
            startLocationUpdates()
            return
        }

        locationPermission.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun startLocationUpdates() {
        if (!hasLocationPermission()) {
            return
        }

        stopLocationUpdates()

        var requested = false

        runCatching {
            if (
                locationManager.isProviderEnabled(
                    LocationManager.GPS_PROVIDER
                )
            ) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1500L,
                    1.0f,
                    locationListener
                )

                requested = true

                locationManager
                    .getLastKnownLocation(
                        LocationManager.GPS_PROVIDER
                    )
                    ?.let {
                        lastLocation = it
                        updateLocationOverlay(it)
                    }
            }
        }

        runCatching {
            if (
                locationManager.isProviderEnabled(
                    LocationManager.NETWORK_PROVIDER
                )
            ) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    3000L,
                    2.0f,
                    locationListener
                )

                requested = true

                if (lastLocation == null) {
                    locationManager
                        .getLastKnownLocation(
                            LocationManager.NETWORK_PROVIDER
                        )
                        ?.let {
                            lastLocation = it
                            updateLocationOverlay(it)
                        }
                }
            }
        }

        locationUpdatesActive =
            requested

        if (!requested) {
            updateLocationLabel(
                "GPS kapalı",
                Color.rgb(
                    220,
                    130,
                    90
                )
            )
        }
    }

    private fun stopLocationUpdates() {
        if (
            !::locationManager.isInitialized
        ) return

        if (
            !locationUpdatesActive
        ) return

        runCatching {
            locationManager.removeUpdates(
                locationListener
            )
        }

        locationUpdatesActive = false
    }

    private fun updateLocationOverlay(
        location: Location
    ) {
        val accuracy =
            if (
                location.hasAccuracy()
            ) {
                location.accuracy
            } else {
                Float.NaN
            }

        val drawing =
            activeDrawing

        if (
            drawing == null
        ) {
            mapView.locationAccuracyMeters =
                accuracy.takeIf {
                    it.isFinite()
                }

            showAccuracyOnly(accuracy)
            return
        }

        if (
            !drawing.hasProjectedOrigin
        ) {
            mapView.userLocation = null
            focusButton.isEnabled = false

            updateLocationLabel(
                accuracyText(accuracy) +
                    " • FOTONMAP koordinatı uygun değil",
                Color.rgb(
                    220,
                    170,
                    80
                )
            )

            return
        }

        val projectCenterEasting =
            drawing.originX +
                drawing.center.x

        val projectCenterNorthing =
            drawing.originY +
                drawing.center.y

        val activeCentralMeridian =
            ItrfTmProjection
                .bestCentralMeridian(
                    latitudeDeg =
                        location.latitude,
                    longitudeDeg =
                        location.longitude,
                    projectEasting =
                        projectCenterEasting,
                    projectNorthing =
                        projectCenterNorthing,
                    preferred =
                        drawing.centralMeridian
                )

        mapView
            .setCentralMeridianOverride(
                activeCentralMeridian
            )

        val projected =
            ItrfTmProjection.forward(
                latitudeDeg =
                    location.latitude,
                longitudeDeg =
                    location.longitude,
                centralMeridianDeg =
                    activeCentralMeridian
            )

        val local =
            Pt(
                projected.easting -
                    drawing.originX,
                projected.northing -
                    drawing.originY
            )

        mapView.userLocation =
            local

        mapView.locationAccuracyMeters =
            accuracy.takeIf {
                it.isFinite()
            }

        refreshRouteGuide()

        focusButton.isEnabled =
            true

        val outside =
            local.x <
                drawing.minX ||
                local.x >
                drawing.maxX ||
                local.y <
                drawing.minY ||
                local.y >
                drawing.maxY

        val text =
            buildString {
                append(
                    accuracyText(
                        accuracy
                    )
                )

                drawing.epsg?.let {
                    append(" • EPSG:")
                    append(it)
                }

                if (outside) {
                    append(" • plan dışında")
                }
            }

        updateLocationLabel(
            text,
            accuracyColor(
                accuracy
            )
        )
    }

    private fun showAccuracyOnly(
        accuracy: Float
    ) {
        updateLocationLabel(
            accuracyText(
                accuracy
            ),
            accuracyColor(
                accuracy
            )
        )
    }

    private fun accuracyText(
        accuracy: Float
    ): String =
        if (
            accuracy.isFinite()
        ) {
            "GPS ±" +
                String.format(
                    Locale.US,
                    "%.1f",
                    accuracy
                )
                .replace(
                    '.',
                    ','
                ) +
                " m"
        } else {
            "GPS doğruluğu bilinmiyor"
        }

    private fun accuracyColor(
        accuracy: Float
    ): Int =
        when {
            !accuracy.isFinite() ->
                Color.rgb(
                    160,
                    180,
                    200
                )

            accuracy <= 5f ->
                Color.rgb(
                    80,
                    220,
                    140
                )

            accuracy <= 12f ->
                Color.rgb(
                    235,
                    185,
                    70
                )

            else ->
                Color.rgb(
                    230,
                    105,
                    95
                )
        }

    private fun updateLocationLabel(
        text: String,
        color: Int
    ) {
        if (
            !::locationLabel.isInitialized
        ) return

        locationLabel.text =
            text

        locationLabel.setTextColor(
            color
        )
    }

    private fun focusOnCurrentLocation() {
        if (
            !hasLocationPermission()
        ) {
            ensureLocationPermission()
            return
        }

        if (
            lastLocation == null
        ) {
            Toast.makeText(
                this,
                "GPS konumu henüz alınmadı.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

        if (
            !mapView.focusOnUserLocation()
        ) {
            Toast.makeText(
                this,
                "Konum FOTONMAP üzerine yerleştirilemedi.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun refreshRouteGuide() {
        if (
            !::routeBar.isInitialized ||
            !::mapView.isInitialized
        ) return

        val info =
            currentInfo

        val target =
            mapView.routeTarget

        if (
            info == null ||
            target == null ||
            info.segment.id != target.id
        ) {
            routeBar.visibility =
                View.GONE

            return
        }

        routeBar.visibility =
            View.VISIBLE

        val distance =
            mapView.routeDistanceMeters()

        routeLabel.text =
            if (distance == null) {
                "${info.sectionNo}   •   GPS bekleniyor"
            } else {
                val value =
                    String.format(
                        Locale.US,
                        "%.1f",
                        distance
                    )
                        .replace(
                            '.',
                            ','
                        )

                if (distance <= 3.0) {
                    "${info.sectionNo}   •   $value m   •   YAKLAŞTIN"
                } else {
                    "${info.sectionNo}   •   $value m"
                }
            }
    }

    private fun closeRouteGuide() {
        if (
            ::mapView.isInitialized
        ) {
            mapView.clearRouteGuide()
        }

        if (
            ::routeBar.isInitialized
        ) {
            routeBar.visibility =
                View.GONE
        }
    }

    private fun refreshSelection() {
        val info = currentInfo
        if (info == null) {
            selectionLabel.text = "Bir hatta dokun"
            reverseButton.isEnabled = false
            confirmButton.isEnabled = false
            undoButton.isEnabled = false
            return
        }
        val length = String.format(Locale.US, "%.2f", info.lengthMeters).replace('.', ',')
        selectionLabel.text = "${info.sectionNo}   •   $length m"
        reverseButton.isEnabled = true
        confirmButton.isEnabled = true
        undoButton.isEnabled = completed.contains(info.segment.id)
    }

    private fun confirmSelection() {
        val uri = activeUri ?: return
        val info = currentInfo ?: return
        if (info.startName == "?" || info.endName == "?") {
            AlertDialog.Builder(this)
                .setTitle("Baca adı eksik")
                .setMessage("FOTONMAP bu hattın iki baca adından en az birini içermiyor. Yine de tamamlandı işaretlensin mi?")
                .setNegativeButton("İptal", null)
                .setPositiveButton("İşaretle") { _, _ -> saveAndTransfer(uri, info) }
                .show()
        } else saveAndTransfer(uri, info)
    }

    private fun saveAndTransfer(uri: Uri, info: SelectedLineInfo) {
        completed.add(info.segment.id)
        completionStore.save(uri, completed)

        val fileSave =
            runCatching {
                FotonMapFileStore.writeCompleted(
                    this,
                    uri,
                    completed
                )
            }

        mapView.completedIds = completed
        updateCount()
        undoButton.isEnabled = true

        fileSave.onFailure { err ->
            Toast.makeText(
                this,
                "Hat yeşil işaretlendi fakat FOTONMAP dosyasına yazılamadı: ${err.message}",
                Toast.LENGTH_LONG
            ).show()
        }

        val payload = RobotSelection(
            sectionNo = info.sectionNo,
            startManhole = info.startName,
            endManhole = info.endName,
            lengthMeters = info.lengthMeters
        )

        // Robot'a otomatik geri yazma yok.
        // Seçilen hattı kalıcı "Son Seçilen Hat" olarak tutuyoruz.
        ImeSelectionStore.saveLastSection(this, payload.sectionNo)
        ImeSelectionStore.cancelSelectionRequest(this)

        status.text =
            "${payload.sectionNo} kaydedildi. Robot'ta Boru Kesit No alanına dokun; FOTON Klavye üstündeki '${payload.sectionNo} YAZ' düğmesine bas."

        Toast.makeText(
            this,
            "Son seçilen hat: ${payload.sectionNo}",
            Toast.LENGTH_SHORT
        ).show()

        if (launchedFromIme) {
            finish()
            overridePendingTransition(0, 0)
        }
    }

    private fun toggleCompleted(done: Boolean) {
        val uri = activeUri ?: return
        val id = currentInfo?.segment?.id ?: return
        if (done) completed.add(id) else completed.remove(id)
        completionStore.save(uri, completed)

        val fileSave =
            runCatching {
                FotonMapFileStore.writeCompleted(
                    this,
                    uri,
                    completed
                )
            }

        mapView.completedIds = completed
        updateCount()
        refreshSelection()

        fileSave.onFailure { err ->
            Toast.makeText(
                this,
                "FOTONMAP dosyasına güncel durum yazılamadı: ${err.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun updateCount() {
        countLabel.text = "${completed.size} / ${activeDrawing?.segments?.size ?: 0} tamamlandı"
    }

    private fun requestPdf() {
        if (activeDrawing == null) {
            Toast.makeText(this, "Önce FOTONMAP aç", Toast.LENGTH_SHORT).show()
            return
        }
        val clean = activeName.substringBeforeLast('.').ifBlank { "FOTONMAP" }
            .replace(Regex("[^A-Za-z0-9ÇĞİÖŞÜçğıöşü_-]+"), "_")
        createPdf.launch("${clean}_durum_plani.pdf")
    }

    private fun exportPdf(uri: Uri) {
        val drawing = activeDrawing ?: return
        val snapshot = completed.toSet()
        status.text = "PDF hazırlanıyor..."
        executor.execute {
            val result = runCatching {
                contentResolver.openOutputStream(uri).use { output ->
                    requireNotNull(output) { "PDF dosyası açılamadı" }
                    MapPdfExporter.write(output, drawing, snapshot, activeName)
                }
            }
            runOnUiThread {
                result.onSuccess {
                    status.text = "PDF oluşturuldu: ${activeName.substringBeforeLast('.')} durum planı"
                    Toast.makeText(this, "PDF oluşturuldu", Toast.LENGTH_LONG).show()
                }.onFailure { err ->
                    status.text = "PDF oluşturulamadı: ${err.message}"
                    Toast.makeText(this, "PDF oluşturulamadı", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun copyFallback(payload: RobotSelection) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Boru Kesit No", payload.sectionNo))
        Toast.makeText(this, "${payload.sectionNo} panoya kopyalandı", Toast.LENGTH_SHORT).show()
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) return c.getString(0) ?: "proje.fotonmap"
        }
        return uri.lastPathSegment ?: "proje.fotonmap"
    }

    private fun button(
        text: String,
        onClick: () -> Unit
    ): Button =
        Button(this).apply {
            this.text =
                text

            isAllCaps =
                false

            textSize =
                11.5f

            minHeight =
                0
            minimumHeight =
                0

            minWidth =
                0
            minimumWidth =
                0

            setPadding(
                dp(10),
                0,
                dp(10),
                0
            )

            setOnClickListener {
                onClick()
            }
        }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_FROM_IME = "from_ime"
    }
}
