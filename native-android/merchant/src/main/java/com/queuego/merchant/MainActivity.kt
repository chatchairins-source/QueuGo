package com.queuego.merchant

import com.queuego.shared.nativeLogoutScope

import android.Manifest
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.queuego.shared.NativeAuth
import com.queuego.shared.NativeAuthApi
import com.queuego.shared.NativeMerchantMarketRegistration
import com.queuego.shared.NativeMerchantMarketRequest
import com.queuego.shared.NativeMerchantRegistration
import com.queuego.shared.NativeRegistrationMarket
import com.queuego.shared.QgGreen
import com.queuego.shared.QgMapPoint
import com.queuego.shared.QgMuted
import com.queuego.shared.QgRed
import com.queuego.shared.NativeSessionInvalidException
import com.queuego.shared.QueueGoTheme
import com.queuego.shared.SecureRoleSessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MerchantNativeEntryGate() }
    }
}

@Composable
private fun MerchantNativeEntryGate() {
    val context = LocalContext.current
    val store = remember { SecureRoleSessionStore(context, "shop") }
    val api = remember { NativeAuthApi() }
    val scope = rememberCoroutineScope()
    val logoutScope = remember(context) { nativeLogoutScope(context, scope) }
    var current by remember { mutableStateOf(store.load()) }

    LaunchedEffect(Unit) {
        while (true) {
            val saved = store.load()
            if (saved?.session?.accessToken != current?.session?.accessToken ||
                saved?.user?.role != current?.user?.role
            ) current = saved
            delay(350)
        }
    }

    when {
        current?.user?.role == "pos_staff" -> QueueGoTheme {
            PosStaffNativeHost(
                initialAuth = current!!,
                store = store,
                onLogout = {
                    current?.session?.let { session -> logoutScope.launch { api.revoke(session) } }
                    store.clear()
                    current = null
                }
            )
        }
        current != null -> QueueGoMerchantApp()
        else -> QueueGoTheme {
            MerchantNativeAuthentication(
                store = store,
                onAuthenticated = { current = store.load() }
            )
        }
    }
}

@Composable
private fun PosStaffNativeHost(
    initialAuth: NativeAuth,
    store: SecureRoleSessionStore,
    onLogout: () -> Unit
) {
    val api = remember { NativeAuthApi() }
    var auth by remember { mutableStateOf(initialAuth) }
    var recoveryError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(initialAuth.session.authUserId) {
        while (true) {
            try {
                val validated = api.validatePosStaff(auth) { refreshed ->
                    store.save(refreshed)
                    auth = refreshed
                }
                store.save(validated)
                auth = validated
                recoveryError = null
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (failure is NativeSessionInvalidException) {
                    store.clear()
                    onLogout()
                    return@LaunchedEffect
                }
                recoveryError = failure.message
            }
            delay(25_000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        if (!recoveryError.isNullOrBlank()) {
            Text(
                "การเชื่อมต่อ POS ขัดข้อง ระบบจะลองใหม่อัตโนมัติ",
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp)
            )
        }
        Box(Modifier.weight(1f)) {
            MerchantPosScreen(auth = auth, onBack = onLogout)
        }
    }
}

@Composable
private fun MerchantNativeAuthentication(
    store: SecureRoleSessionStore,
    onAuthenticated: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember { NativeAuthApi() }

    var register by rememberSaveable { mutableStateOf(false) }
    var staffJoin by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    var loginId by rememberSaveable { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var showLoginPassword by remember { mutableStateOf(false) }
    var staffName by rememberSaveable { mutableStateOf("") }
    var staffEmail by rememberSaveable { mutableStateOf("") }
    var staffPassword by remember { mutableStateOf("") }
    var staffSecret by rememberSaveable { mutableStateOf("") }

    var shopName by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var categoryOpen by remember { mutableStateOf(false) }
    var contactName by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var shoppingSubcategories by rememberSaveable { mutableStateOf(setOf<String>()) }
    var truth by rememberSaveable { mutableStateOf(false) }
    var beta by rememberSaveable { mutableStateOf(false) }
    var signupCheckpoint by rememberSaveable { mutableStateOf("") }

    var marketPoint by remember { mutableStateOf<QgMapPoint?>(null) }
    var markets by remember { mutableStateOf<List<NativeRegistrationMarket>>(emptyList()) }
    var selectedMarket by rememberSaveable { mutableStateOf("") }
    var requestNewMarket by rememberSaveable { mutableStateOf(false) }
    var findingMarkets by remember { mutableStateOf(false) }
    var stall by rememberSaveable { mutableStateOf("") }
    var zone by rememberSaveable { mutableStateOf("") }
    var confirmPlace by rememberSaveable { mutableStateOf(false) }
    var confirmSeller by rememberSaveable { mutableStateOf(false) }
    var marketName by rememberSaveable { mutableStateOf("") }
    var province by rememberSaveable { mutableStateOf("บุรีรัมย์") }
    var district by rememberSaveable { mutableStateOf("เมืองบุรีรัมย์") }
    var subdistrict by rememberSaveable { mutableStateOf("") }
    var marketAddress by rememberSaveable { mutableStateOf("") }

    var forgotDialog by remember { mutableStateOf(false) }
    var forgotEmail by rememberSaveable { mutableStateOf("") }

    val categories = listOf(
        "food" to "ร้านอาหาร",
        "grocery" to "ร้านขายของชำ",
        "cafe" to "เครื่องดื่ม / คาเฟ่",
        "laundry" to "ร้านฝากซัก",
        "market" to "ตลาดสด",
        "shopping" to "ช้อปปิ้ง",
        "other" to "อื่น ๆ"
    )
    val shopping = listOf(
        "mobile_accessories" to "มือถือและอุปกรณ์",
        "computer_it" to "คอมพิวเตอร์และไอที",
        "automotive_car" to "อะไหล่รถยนต์",
        "automotive_motorcycle" to "อะไหล่มอเตอร์ไซค์",
        "fashion_accessories" to "เสื้อผ้าและเครื่องประดับ",
        "toys" to "ของเล่น",
        "home_decor" to "ตกแต่งบ้าน"
    )

    fun login() {
        if (busy) return
        if (loginId.isBlank() || loginPassword.isBlank()) {
            error = "กรุณากรอกเบอร์โทรศัพท์หรืออีเมลและรหัสผ่าน"
            return
        }
        busy = true
        error = null
        message = null
        scope.launch {
            try {
                val auth = api.signInMerchantOrStaff(loginId.trim(), loginPassword, store.deviceId())
                store.save(auth)
                onAuthenticated()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = failure.message ?: "เข้าสู่ระบบไม่สำเร็จ"
            } finally {
                busy = false
            }
        }
    }

    fun joinStaff() {
        if (busy) return
        error = null
        message = null
        busy = true
        scope.launch {
            try {
                val auth = api.joinPosStaff(
                    staffName.trim(),
                    staffEmail.trim(),
                    staffPassword,
                    staffSecret.trim()
                )
                store.save(auth)
                onAuthenticated()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = failure.message ?: "เข้าร่วมร้านไม่สำเร็จ"
            } finally {
                busy = false
            }
        }
    }

    fun buildRegistration(): NativeMerchantRegistration {
        val market = if (category == "market") {
            val point = marketPoint ?: throw IllegalArgumentException(
                "กรุณากดค้นหาตลาดใกล้ร้านและอนุญาตพิกัดก่อนสมัคร"
            )
            val selected = markets.firstOrNull { it.id == selectedMarket }
            if (!requestNewMarket && selected == null) {
                throw IllegalArgumentException("กรุณาเลือกตลาดอีกครั้ง")
            }
            if (selected != null && !requestNewMarket && !selected.selectable) {
                throw IllegalArgumentException(
                    "พิกัดร้านอยู่นอกรัศมีของตลาดนี้ กรุณาเลือกตลาดที่ใกล้กว่าหรือขอเพิ่มตลาดใหม่"
                )
            }
            NativeMerchantMarketRegistration(
                point.latitude,
                point.longitude,
                if (requestNewMarket) null else selected?.id,
                stall.trim().takeIf { it.isNotBlank() },
                zone.trim().takeIf { it.isNotBlank() },
                if (requestNewMarket) NativeMerchantMarketRequest(
                    marketName.trim(),
                    province.trim(),
                    district.trim().takeIf { it.isNotBlank() },
                    subdistrict.trim().takeIf { it.isNotBlank() },
                    marketAddress.trim().takeIf { it.isNotBlank() }
                ) else null,
                confirmPlace,
                confirmSeller
            )
        } else null
        return NativeMerchantRegistration(
            contactName.trim(),
            shopName.trim(),
            category,
            shoppingSubcategories,
            phone.trim(),
            password,
            confirmation,
            truth,
            beta,
            market
        ).also { it.validate() }
    }

    fun submitRegistration() {
        if (busy) return
        error = null
        message = null
        val form = try {
            buildRegistration()
        } catch (failure: IllegalArgumentException) {
            error = failure.message
            return
        }
        val id = form.normalizedPhone
        val skipSignup = signupCheckpoint == id
        if (!skipSignup) signupCheckpoint = id
        busy = true
        scope.launch {
            try {
                val auth = api.registerMerchant(form, store.deviceId(), skipSignup)
                store.save(auth)
                onAuthenticated()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                if (!skipSignup && failure is com.queuego.shared.NativeAuthHttpException &&
                    failure.statusCode in 400..499
                ) signupCheckpoint = ""
                error = failure.message ?: "สมัครร้านค้าไม่สำเร็จ"
            } finally {
                busy = false
            }
        }
    }

    fun readMarkets() {
        if (busy || findingMarkets) return
        findingMarkets = true
        error = null
        message = null
        scope.launch {
            try {
                val location = merchantFreshRegistrationLocation(context)
                val point = QgMapPoint(location.latitude, location.longitude, 0)
                require(point.valid) { "พิกัดร้านไม่ถูกต้อง กรุณาลองใหม่" }
                val rows = api.loadMerchantRegistrationMarkets(point.latitude, point.longitude)
                marketPoint = point
                markets = rows
                selectedMarket = (rows.firstOrNull { it.selectable } ?: rows.firstOrNull())?.id.orEmpty()
                requestNewMarket = rows.none { it.selectable }
                message = if (rows.isEmpty())
                    "ยังไม่มีตลาดในจังหวัดบุรีรัมย์ที่เปิดใช้ กรุณาขอเพิ่มตลาดใหม่"
                else "อ่านพิกัดร้านและเรียงตลาดที่ใกล้ที่สุดแล้ว"
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                error = failure.message ?: "อ่านพิกัดร้านไม่สำเร็จ กรุณาลองใหม่"
            } finally {
                findingMarkets = false
            }
        }
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) readMarkets()
        else error = "กรุณาอนุญาตการใช้ตำแหน่งเพื่อสมัครร้านในตลาด"
    }

    fun requestMarkets() {
        if (merchantHasLocationPermission(context)) readMarkets()
        else locationPermission.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    BackHandler((register || staffJoin) && !busy) {
        register = false
        staffJoin = false
        error = null
        message = null
        password = ""
        confirmation = ""
        staffPassword = ""
    }

    if (forgotDialog) {
        AlertDialog(
            onDismissRequest = { if (!busy) forgotDialog = false },
            title = { Text("ลืมรหัสผ่าน?") },
            text = {
                OutlinedTextField(
                    forgotEmail,
                    { forgotEmail = it },
                    label = { Text("อีเมลบัญชีร้านค้า") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                api.recoverPassword(forgotEmail)
                                message = "ส่งลิงก์ตั้งรหัสผ่านใหม่แล้ว"
                                forgotDialog = false
                            } catch (failure: Exception) {
                                error = failure.message ?: "ส่งลิงก์ไม่สำเร็จ"
                            } finally {
                                busy = false
                            }
                        }
                    }
                ) { Text("ส่งลิงก์") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { forgotDialog = false }) { Text("ยกเลิก") }
            }
        )
    }

    if (!register && !staffJoin) {
        MerchantProductionLogin(
            loginId, { loginId = it }, loginPassword, { loginPassword = it },
            showLoginPassword, { showLoginPassword = !showLoginPassword }, busy, error, message,
            onLogin = ::login,
            onForgot = {
                forgotEmail = loginId.takeIf { it.contains("@") }.orEmpty()
                forgotDialog = true
            },
            onRegister = { register = true; staffJoin = false; error = null; message = null; loginPassword = "" },
            onStaff = { staffJoin = true; register = false; error = null; message = null; loginPassword = "" }
        )
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .imePadding()
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(if (register || staffJoin) 250.dp else 360.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.qgm_merchant_photo),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alignment = Alignment.Center
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.46f))
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MerchantQueueGoMark(Modifier.size(42.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("Queue", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Go", color = Color(0xFFF0092D), fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    when {
                        staffJoin -> "พนักงานหน้าร้าน"
                        register -> "สมัครร้านค้ากับ QueueGo"
                        else -> "เข้าสู่ระบบร้านค้า"
                    },
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    when {
                        staffJoin -> "ใช้บัญชีพนักงานของคุณเอง\nและรหัสเชิญจากเจ้าของร้าน"
                        register -> "ลงทะเบียนร้านไว้ล่วงหน้า\nเพื่อเตรียมสินค้าให้พร้อมก่อนเปิดบริการ"
                        else -> "จัดการคิวร้านของคุณ\nให้ง่ายขึ้น ในทุก ๆ วัน"
                    },
                    color = Color.White,
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                )
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .offset(y = (-16).dp)
                .background(Color.White, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            if (staffJoin) {
                MerchantEntrySection(
                    "เข้าร่วมร้านค้า",
                    "ใช้บัญชีพนักงานของคุณเองและรหัสเชิญจากเจ้าของร้าน"
                ) {
                    MerchantEntryField("ชื่อพนักงาน", staffName, { staffName = it.take(100) }, "ชื่อพนักงาน", !busy)
                    MerchantEntryField("อีเมล", staffEmail, { staffEmail = it }, "อีเมล", !busy, KeyboardType.Email)
                    MerchantEntryPassword("รหัสผ่าน", staffPassword, { staffPassword = it }, "อย่างน้อย 8 ตัว", !busy)
                    MerchantEntryField("รหัสเชิญ", staffSecret, { staffSecret = it.trim() }, "รหัสเชิญจากเจ้าของร้าน", !busy)
                    Button(
                        onClick = ::joinStaff,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(54.dp)
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Text("สมัครและเข้าร่วมร้าน", fontWeight = FontWeight.Bold)
                    }
                }
                MerchantEntryDivider("มีบัญชีแล้ว")
                OutlinedButton(
                    onClick = {
                        staffJoin = false
                        error = null
                        message = null
                        staffPassword = ""
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("กลับไปเข้าสู่ระบบ", color = QgRed, fontWeight = FontWeight.Bold) }
            } else {
                MerchantEntryField("ชื่อร้าน *", shopName, { shopName = it.take(100) }, "ชื่อร้านที่ลูกค้าจะเห็น", !busy)
                Text("ประเภทร้านค้า *", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Box {
                    OutlinedButton(
                        onClick = { categoryOpen = true },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Text(
                            categories.firstOrNull { it.first == category }?.second ?: "เลือกประเภทร้านค้า",
                            modifier = Modifier.weight(1f)
                        )
                        Text("⌄")
                    }
                    DropdownMenu(categoryOpen, { categoryOpen = false }) {
                        categories.forEach { pair ->
                            DropdownMenuItem(
                                text = { Text(pair.second) },
                                onClick = {
                                    category = pair.first
                                    categoryOpen = false
                                    if (category != "shopping") shoppingSubcategories = emptySet()
                                    if (category != "market") {
                                        marketPoint = null
                                        markets = emptyList()
                                        selectedMarket = ""
                                        requestNewMarket = false
                                    }
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (category == "shopping") {
                    MerchantEntrySection(
                        "ร้านของคุณขายสินค้าอะไร",
                        "เลือกได้มากกว่า 1 หมวด ใช้สินค้า สต๊อก ตะกร้า และการจัดส่งระบบเดิม"
                    ) {
                        shopping.forEach { pair ->
                            MerchantEntryCheck(
                                pair.first in shoppingSubcategories,
                                !busy,
                                pair.second
                            ) { checked ->
                                shoppingSubcategories = if (checked)
                                    shoppingSubcategories + pair.first
                                else shoppingSubcategories - pair.first
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                if (category == "market") {
                    MerchantEntrySection(
                        "ยืนยันตลาดที่ร้านตั้งอยู่",
                        "QueueGo จะใช้พิกัดหน้าร้านเพื่อแนะนำตลาดที่ใกล้ที่สุด ร้านจะเข้าหน้าตลาดสดได้หลัง Admin ตรวจสอบและอนุมัติ"
                    ) {
                        OutlinedButton(
                            onClick = ::requestMarkets,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (findingMarkets) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text("ใช้พิกัดร้านนี้และค้นหาตลาดใกล้ฉัน")
                        }
                        Spacer(Modifier.height(7.dp))
                        markets.take(5).forEach { market ->
                            val chosen = !requestNewMarket && selectedMarket == market.id
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                                    .border(
                                        1.dp,
                                        if (chosen) QgRed else Color(0xFFE2E5E8),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable(enabled = !busy && market.selectable) {
                                        requestNewMarket = false
                                        selectedMarket = market.id
                                    }
                                    .padding(9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(market.name, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(
                                        String.format(java.util.Locale.US, "%.2f กม.", market.distanceKm),
                                        fontSize = 10.sp,
                                        color = QgMuted
                                    )
                                }
                                Text(
                                    if (chosen) "เลือกแล้ว" else if (market.selectable) "เลือก" else "นอกรัศมี",
                                    color = if (market.selectable) QgRed else QgMuted,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        MerchantEntryCheck(
                            requestNewMarket,
                            !busy && marketPoint != null,
                            "ไม่พบตลาดของฉัน / ขอเพิ่มตลาดใหม่"
                        ) { requestNewMarket = it }
                        if (requestNewMarket) {
                            MerchantEntryField("ชื่อตลาด", marketName, { marketName = it.take(160) }, "เช่น ตลาดสดสวายจีก", !busy)
                            MerchantEntryField("จังหวัด", province, { province = it.take(100) }, "", !busy)
                            MerchantEntryField("อำเภอ", district, { district = it.take(100) }, "", !busy)
                            MerchantEntryField("ตำบล", subdistrict, { subdistrict = it.take(100) }, "", !busy)
                            MerchantEntryField("ที่อยู่/รายละเอียดตลาด", marketAddress, { marketAddress = it.take(500) }, "", !busy)
                        }
                        MerchantEntryField("เลขแผง (ถ้ามี)", stall, { stall = it.take(80) }, "เช่น A12", !busy)
                        MerchantEntryField("โซน (ถ้ามี)", zone, { zone = it.take(80) }, "เช่น โซนผัก", !busy)
                        MerchantEntryCheck(
                            confirmPlace,
                            !busy,
                            "ฉันยืนยันว่า ร้าน/แผงของฉันตั้งอยู่ในตลาดที่เลือกหรือระบุจริง"
                        ) { confirmPlace = it }
                        MerchantEntryCheck(
                            confirmSeller,
                            !busy,
                            "ฉันยืนยันว่า ฉันเป็นผู้ค้าหรือมีร้าน/แผงขายอยู่ในตลาดนี้จริง"
                        ) { confirmSeller = it }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                MerchantEntryField("ชื่อผู้ติดต่อ", contactName, { contactName = it.take(120) }, "ชื่อผู้ติดต่อ", !busy)
                MerchantEntryField(
                    "เบอร์โทรศัพท์",
                    phone,
                    { phone = it.filter { char -> char.isDigit() || char == '-' || char == ' ' }.take(12) },
                    "เบอร์โทรศัพท์ 10 หลัก",
                    !busy,
                    KeyboardType.Phone
                )
                MerchantEntryPassword("รหัสผ่าน", password, { password = it }, "อย่างน้อย 12 ตัว: A-Z, a-z, ตัวเลข, สัญลักษณ์", !busy)
                MerchantEntryPassword("ยืนยันรหัสผ่าน", confirmation, { confirmation = it }, "ยืนยันรหัสผ่าน", !busy)

                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF8F9), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0xFFE2E5E8), RoundedCornerShape(12.dp))
                        .padding(13.dp)
                ) {
                    Text(
                        "QueueGo อยู่ในช่วงทดสอบระบบ (Beta)",
                        color = Color(0xFFD5092B),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 12.sp
                    )
                    Text(
                        "ระบบอาจมีข้อผิดพลาด ข้อมูลล่าช้า ออเดอร์หรือยอดเงินผิดปกติ หรือหยุดให้บริการชั่วคราว ผู้ใช้ต้องตรวจสอบข้อมูลสำคัญก่อนยืนยันทุกครั้ง",
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = Color(0xFF555A62)
                    )
                    MerchantEntryCheck(
                        truth,
                        !busy,
                        "ข้าพเจ้ายืนยันว่าข้อมูลที่ให้เป็นความจริงและมีสิทธิ์ใช้ข้อมูล/รูปภาพที่ส่งให้ QueueGo"
                    ) { truth = it }
                    MerchantEntryCheck(
                        beta,
                        !busy,
                        "ข้าพเจ้าได้อ่านและยอมรับข้อตกลงการใช้บริการและการเข้าร่วมทดสอบ QueueGo และรับทราบว่ายังเป็นระบบ Beta"
                    ) { beta = it }
                }

                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = ::submitRegistration,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("สมัครร้านค้า", fontWeight = FontWeight.Bold)
                }
                MerchantEntryDivider("มีบัญชีแล้ว")
                OutlinedButton(
                    onClick = {
                        register = false
                        error = null
                        message = null
                        password = ""
                        confirmation = ""
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                ) { Text("กลับไปเข้าสู่ระบบ", color = QgRed, fontWeight = FontWeight.Bold) }
            }

            if (!error.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }
            if (!message.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(message!!, color = QgGreen, fontSize = 12.sp)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
internal fun MerchantQueueGoMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val strokeWidth = size.minDimension * (8f / 48f)
        val center = Offset(size.width * (23f / 48f), size.height * (23f / 48f))
        val radius = size.minDimension * (16f / 48f)
        drawCircle(
            color = Color(0xFFEC092E),
            radius = radius,
            center = center,
            style = Stroke(width = strokeWidth)
        )
        drawLine(
            color = Color(0xFFEC092E),
            start = Offset(size.width * (26f / 48f), size.height * (27f / 48f)),
            end = Offset(size.width * (38f / 48f), size.height * (39f / 48f)),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun MerchantEntryDivider(label: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(Modifier.weight(1f), color = Color(0xFFE2E5E8))
        Text(label, color = QgMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp))
        HorizontalDivider(Modifier.weight(1f), color = Color(0xFFE2E5E8))
    }
}

@Composable
private fun MerchantEntryField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    enabled: Boolean,
    keyboard: KeyboardType = KeyboardType.Text
) {
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(5.dp))
    OutlinedTextField(
        value,
        onChange,
        enabled = enabled,
        placeholder = { if (hint.isNotBlank()) Text(hint) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun MerchantEntryPassword(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    hint: String,
    enabled: Boolean
) {
    var visible by remember { mutableStateOf(false) }
    Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(5.dp))
    OutlinedTextField(
        value,
        onChange,
        enabled = enabled,
        placeholder = { Text(hint) },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            Text(
                if (visible) "ซ่อน" else "แสดง",
                color = QgMuted,
                fontSize = 11.sp,
                modifier = Modifier.clickable(enabled = enabled) { visible = !visible }
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun MerchantEntryCheck(
    checked: Boolean,
    enabled: Boolean,
    label: String,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked, if (enabled) onChange else null)
        Text(label, fontSize = 11.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun MerchantEntrySection(
    title: String,
    note: String,
    content: @Composable () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE2E5E8), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        Text(note, fontSize = 10.5.sp, lineHeight = 15.sp, color = QgMuted)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

private suspend fun merchantFreshRegistrationLocation(context: Context): Location =
    withTimeout(15_000L) {
        suspendCancellableCoroutine { continuation ->
            if (!merchantHasLocationPermission(context)) {
                continuation.resumeWithException(
                    SecurityException("กรุณาอนุญาตการใช้ตำแหน่งเพื่อสมัครร้านในตลาด")
                )
                return@suspendCancellableCoroutine
            }
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val providers = listOf(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER
            ).filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
            if (providers.isEmpty()) {
                continuation.resumeWithException(
                    IllegalStateException("กรุณาเปิดตำแหน่งที่ตั้งแล้วลองใหม่")
                )
                return@suspendCancellableCoroutine
            }
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (!continuation.isActive) return
                    runCatching { manager.removeUpdates(this) }
                    if (location.latitude.isFinite() && location.longitude.isFinite()) {
                        continuation.resume(location)
                    } else {
                        continuation.resumeWithException(
                            IllegalStateException("อ่านพิกัดร้านไม่สำเร็จ กรุณาลองใหม่")
                        )
                    }
                }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                @Deprecated("Legacy Android location callback")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }
            continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
            try {
                @Suppress("MissingPermission")
                providers.forEach { manager.requestSingleUpdate(it, listener, null) }
            } catch (failure: Exception) {
                runCatching { manager.removeUpdates(listener) }
                if (continuation.isActive) continuation.resumeWithException(failure)
            }
        }
    }
