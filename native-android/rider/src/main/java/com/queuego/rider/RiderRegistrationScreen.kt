package com.queuego.rider

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import com.queuego.shared.QgIcon

private val RegistrationRed = Color(0xFFE6002D)
private val RegistrationLine = Color(0xFFEEE7E9)

@Composable
internal fun RiderRegistrationScreen(
    modifier: Modifier, store: SessionStore, busy: Boolean, error: String?, resuming: Boolean,
    onBack: () -> Unit, onSubmit: (RiderRegistrationForm, String, Map<String,String>) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var form by remember { mutableStateOf(RiderRegistrationForm()) }
    var password by remember { mutableStateOf("") } // Never persist passwords or consent.
    var documents by remember { mutableStateOf<Map<String,String>>(emptyMap()) }
    var step by rememberSaveable { mutableStateOf(0) }
    var agreement by remember { mutableStateOf(false) }
    var betaAgreement by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }
    var documentBusy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    var selectedDocument by rememberSaveable { mutableStateOf<String?>(null) }
    val scroll = rememberScrollState()
    LaunchedEffect(Unit) {
        try {
            val draft = withContext(Dispatchers.IO) { store.loadRegistrationDraft() }
            if (draft != null) {
                val f = draft.getJSONObject("form")
                form = RiderRegistrationForm(f.optString("name"),f.optString("phone"),f.optString("email"),
                    f.optString("vehicle","motorcycle"),f.optString("capacity"),f.optString("plate"),
                    f.optString("make"),f.optString("model"),f.optString("province"),f.optString("district"),f.optString("area"))
                val d = draft.optJSONObject("documents") ?: JSONObject()
                documents = RIDER_DOCUMENTS.keys.mapNotNull { key -> d.optString(key).takeIf { it.isNotBlank() }?.let { key to it } }.toMap()
            }
            ready = true
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            localError = "อ่านใบสมัครในเครื่องไม่สำเร็จ กรุณาเลือกเอกสารใหม่"
            ready = true
        }
    }
    fun draft(): JSONObject = JSONObject().put("form", JSONObject()
        .put("name",form.name).put("phone",form.phone).put("email",form.email).put("vehicle",form.vehicle)
        .put("capacity",form.capacity).put("plate",form.plate).put("make",form.make).put("model",form.model)
        .put("province",form.province).put("district",form.district).put("area",form.area))
        .put("documents",JSONObject(documents))
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val key = selectedDocument
        if (uri != null && key != null && !busy) {
            documentBusy = true
            scope.launch {
                try {
                    val data = readRiderRegistrationDocument(context,uri,key != "rr-photo" && key != "rr-vehicle-photo")
                    documents = documents + (key to data)
                    withContext(Dispatchers.IO) { store.saveRegistrationDraft(draft()) }
                    localError = null
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    localError = e.message ?: "ไม่สามารถอ่านไฟล์ได้ กรุณาเลือกไฟล์ใหม่"
                } finally { documentBusy = false }
            }
        }
    }
    val locked = busy || documentBusy || !ready
    val back = { if (!locked) { if (step > 0) step -= 1 else onBack() } }
    BackHandler { back() }
    LaunchedEffect(step) { scroll.scrollTo(0) }
    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFFFFFAFA)).imePadding()) {
        val wide = maxWidth > 800.dp
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.widthIn(max=820.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                IconButton(onClick={ if(!locked) onBack() }, enabled=!locked, modifier=Modifier.size(44.dp)
                    .background(Color(0xFFFFF0F3),RoundedCornerShape(50))) { QgIcon("back",Modifier.size(23.dp),RegistrationRed) }
                Column(Modifier.weight(1f)) {
                    Text("สมัครเป็นไรเดอร์",fontSize=24.sp,fontWeight=FontWeight.Bold)
                    Text("กรอกข้อมูลให้ครบเพื่อส่งให้ Admin ตรวจสอบ",fontSize=11.sp,color=Color(0xFF77747B))
                }
                Box(Modifier.size(42.dp).background(RegistrationRed,RoundedCornerShape(13.dp)),contentAlignment=Alignment.Center) {
                    Text("Q",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Black)
                }
            }
            Spacer(Modifier.height(16.dp))
            Surface(Modifier.widthIn(max=820.dp).fillMaxWidth(),shape=RoundedCornerShape(if(wide)28.dp else 22.dp),
                color=Color.White,border=BorderStroke(1.dp,RegistrationLine)) {
                Column(Modifier.padding(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top=4.dp,bottom=22.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                        listOf("ส่วนตัว","รถและพื้นที่","เอกสาร","ยืนยัน").forEachIndexed { index,label ->
                            if(index>0) Box(Modifier.weight(1f).padding(top=13.dp).height(2.dp).background(RegistrationLine))
                            Column(horizontalAlignment=Alignment.CenterHorizontally) {
                                Box(Modifier.size(28.dp).background(if(index<=step)RegistrationRed else Color(0xFFF3EDEF),RoundedCornerShape(50)),contentAlignment=Alignment.Center) {
                                    Text((index+1).toString(),fontSize=9.sp,fontWeight=FontWeight.Bold,color=if(index<=step)Color.White else Color(0xFFAAA0A5))
                                }
                                Spacer(Modifier.height(5.dp))
                                Text(label,fontSize=9.sp,color=if(index==step)RegistrationRed else Color(0xFFAAA0A5))
                            }
                        }
                    }
                    val upload: @Composable (String) -> Unit = { key ->
                        val face=key=="rr-photo"
                        Column(
                            if(face)Modifier.fillMaxWidth() else Modifier.fillMaxWidth()
                                .border(1.dp,Color(0xFFDDCFD4),RoundedCornerShape(16.dp)).padding(12.dp),
                            horizontalAlignment=if(face)Alignment.CenterHorizontally else Alignment.Start
                        ) {
                            if(!face)Text(RIDER_DOCUMENTS.getValue(key)+" (บังคับ)",fontSize=11.sp,fontWeight=FontWeight.ExtraBold)
                            if(face)documents[key]?.let { RegistrationDocumentPreview(it) }
                            RegistrationButton(if(face)"เลือกรูปถ่ายหน้าตรง" else "เลือกไฟล์",false,!locked,Modifier.fillMaxWidth(),compact=true) {
                                selectedDocument=key
                                picker.launch(if(key=="rr-photo"||key=="rr-vehicle-photo")arrayOf("image/*")else arrayOf("image/*","application/pdf"))
                            }
                            Text(if(key in documents)"เลือกไฟล์แล้ว" else if(face)"ยังไม่ได้เลือกรูป" else "ยังไม่ได้เลือกไฟล์",
                                fontSize=10.sp,color=Color(0xFF77747B),modifier=Modifier.padding(vertical=8.dp))
                            if(!face)documents[key]?.let { RegistrationDocumentPreview(it) }
                            if(face)Text("JPG/PNG ไม่เกิน 8 MB • เห็นใบหน้าชัดเจน",fontSize=13.sp)
                        }
                    }
                    val panels: List<@Composable () -> Unit> = when(step) {
                        0 -> listOf(
                            { RegistrationPanel("รูปโปรไฟล์",required=true) { upload("rr-photo") } },
                            { RegistrationPanel("ข้อมูลส่วนตัว") {
                                RegistrationField("ชื่อ - นามสกุล *","เช่น นายสมชาย ใจดี",form.name,locked) { form=form.copy(name=it) }
                                RegistrationField("เบอร์โทรศัพท์ *","0812345678",form.phone,locked,type=KeyboardType.Phone) { form=form.copy(phone=it) }
                                if(!resuming) RegistrationField("รหัสผ่าน *","อย่างน้อย 12 ตัว: A-Z, a-z, ตัวเลข, สัญลักษณ์",password,locked,secret=true) { password=it }
                                RegistrationField("อีเมล (ถ้ามี)","rider@example.com",form.email,locked,type=KeyboardType.Email) { form=form.copy(email=it) }
                            } })
                        1 -> listOf(
                            { RegistrationPanel("ยานพาหนะ") {
                                RegistrationSelect("ประเภทพาหนะ *",form.vehicle,listOf("motorcycle" to "รถจักรยานยนต์","car" to "รถยนต์","bicycle" to "จักรยาน","saleng" to "รถซาเล้ง"),locked) { form=form.copy(vehicle=it) }
                                RegistrationField("ความจุรับงานตลาด (กก.)","เช่น 20",form.capacity,locked,type=KeyboardType.Decimal) { form=form.copy(capacity=it) }
                                RegistrationField("ทะเบียนรถ *","เช่น กข 1234 บุรีรัมย์",form.plate,locked) { form=form.copy(plate=it) }
                                RegistrationField("ยี่ห้อ","เช่น Honda",form.make,locked) { form=form.copy(make=it) }
                                RegistrationField("รุ่น","เช่น Wave",form.model,locked) { form=form.copy(model=it) }
                            } },
                            { RegistrationPanel("พื้นที่ให้บริการ") {
                                val provinces=listOf("กรุงเทพมหานคร","บุรีรัมย์","นครราชสีมา","ขอนแก่น","เชียงใหม่","ชลบุรี","อุบลราชธานี","สุรินทร์","ศรีสะเกษ","มหาสารคาม","ร้อยเอ็ด","สระบุรี","ปทุมธานี","นนทบุรี","สมุทรปราการ","ภูเก็ต")
                                RegistrationSelect("จังหวัด *",form.province,listOf("" to "เลือกจังหวัด")+provinces.map{it to it},locked) { form=form.copy(province=it) }
                                RegistrationField("อำเภอ/เขต *","เช่น เมืองบุรีรัมย์",form.district,locked) { form=form.copy(district=it) }
                                RegistrationField("พื้นที่เพิ่มเติม","เช่น ในเมือง / ตลาดสด",form.area,locked) { form=form.copy(area=it) }
                            } })
                        2 -> listOf({ RegistrationPanel("เอกสารประกอบ",required=true) { RIDER_DOCUMENTS.keys.drop(1).forEach { upload(it); Spacer(Modifier.height(9.dp)) } } })
                        else -> listOf({ RegistrationPanel("ยืนยันข้อมูล") {
                            RegistrationAgreement(agreement,!locked,"ข้าพเจ้ารับรองว่าข้อมูลและเอกสารเป็นความจริง และยินยอมให้ QueueGo ตรวจสอบ") { agreement=it }
                            RegistrationAgreement(betaAgreement,!locked,"ข้าพเจ้ารับทราบว่า QueueGo อยู่ในช่วง Beta และจะตรวจสอบข้อมูลสำคัญก่อนดำเนินการ") { betaAgreement=it }
                            Text("หลังส่งใบสมัคร สถานะจะเป็น “รอ Admin ตรวจสอบ” และยังรับงานไม่ได้จนกว่าจะอนุมัติ",fontSize=11.sp,lineHeight=17.sp,
                                modifier=Modifier.background(Color(0xFFFFF2F4),RoundedCornerShape(14.dp)).padding(12.dp))
                        } })
                    }
                    if(wide && panels.size==2) Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                        panels.forEach { panel -> Box(Modifier.weight(1f)) { panel() } }
                    } else Column(verticalArrangement=Arrangement.spacedBy(14.dp)) { panels.forEach { it() } }
                    val message=localError?:error
                    if(message!=null)Text(message,color=RegistrationRed,fontSize=11.sp,modifier=Modifier.padding(top=12.dp))
                    if(locked)LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=12.dp),color=RegistrationRed)
                    Row(Modifier.fillMaxWidth().padding(top=16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        RegistrationButton("ย้อนกลับ",false,!locked,Modifier.weight(1f)) { back() }
                        RegistrationButton(if(step==3)"ส่งใบสมัคร  →"else"ถัดไป  →",true,!locked,Modifier.weight(1.4f)) {
                            localError=form.validate(step,password,documents.keys,resuming)
                            if(step==3) {
                                for(s in 0..2) { val problem=form.validate(s,password,documents.keys,resuming); if(problem!=null){localError=problem;step=s;break} }
                                if(localError==null && (!agreement||!betaAgreement))localError="กรุณายืนยันข้อมูลและยอมรับข้อตกลง Beta ก่อนส่งใบสมัคร"
                            }
                            if(localError==null)scope.launch {
                                documentBusy=true
                                try {
                                    withContext(Dispatchers.IO){store.saveRegistrationDraft(draft())}
                                    if(step<3)step+=1 else onSubmit(form,password,documents)
                                } catch(e:Exception) { if(e is CancellationException)throw e;localError="บันทึกใบสมัครในเครื่องไม่สำเร็จ กรุณาลองใหม่" }
                                finally { documentBusy=false }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable private fun RegistrationPanel(title:String,required:Boolean=false,content:@Composable ColumnScope.()->Unit) {
    Column(Modifier.fillMaxWidth().border(1.dp,RegistrationLine,RoundedCornerShape(20.dp)).padding(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom=10.dp),horizontalArrangement=Arrangement.SpaceBetween) {
            Text(title,fontSize=17.sp,fontWeight=FontWeight.Bold)
            if(required)Text("บังคับ",fontSize=11.sp,color=RegistrationRed,fontWeight=FontWeight.ExtraBold)
        }
        content()
    }
}
@Composable private fun RegistrationField(label:String,hint:String,value:String,locked:Boolean,secret:Boolean=false,type:KeyboardType=KeyboardType.Text,change:(String)->Unit) {
    Column(Modifier.padding(vertical=10.dp)) {
        Text(label,fontSize=11.sp,fontWeight=FontWeight.ExtraBold)
        BasicTextField(value,change,enabled=!locked,singleLine=true,textStyle=androidx.compose.ui.text.TextStyle(fontSize=16.sp,color=Color(0xFF17171B)),
            cursorBrush=SolidColor(RegistrationRed),keyboardOptions=KeyboardOptions(keyboardType=if(secret)KeyboardType.Password else type),
            visualTransformation=if(secret)PasswordVisualTransformation()else VisualTransformation.None,
            modifier=Modifier.padding(top=6.dp).fillMaxWidth().height(50.dp).border(1.dp,Color(0xFFE6DFE2),RoundedCornerShape(15.dp)),
            decorationBox={inner->Box(Modifier.fillMaxSize().padding(horizontal=14.dp),contentAlignment=Alignment.CenterStart){if(value.isEmpty())Text(hint,fontSize=16.sp,color=Color(0xFF757575));inner()}})
    }
}
@Composable private fun RegistrationSelect(label:String,value:String,options:List<Pair<String,String>>,locked:Boolean,change:(String)->Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(vertical=10.dp)) {
        Text(label,fontSize=11.sp,fontWeight=FontWeight.ExtraBold)
        Box {
            OutlinedButton(onClick={expanded=true},enabled=!locked,modifier=Modifier.padding(top=6.dp).fillMaxWidth().height(50.dp),shape=RoundedCornerShape(15.dp),border=BorderStroke(1.dp,Color(0xFFE6DFE2))) {
                Text(options.firstOrNull{it.first==value}?.second.orEmpty(),fontSize=16.sp,color=Color(0xFF17171B),modifier=Modifier.weight(1f));Text("⌄",color=Color(0xFF17171B))
            }
            DropdownMenu(expanded,onDismissRequest={expanded=false}) { options.forEach { (key,label)->DropdownMenuItem(text={Text(label)},onClick={change(key);expanded=false}) } }
        }
    }
}
@Composable private fun RegistrationButton(label:String,primary:Boolean,enabled:Boolean,modifier:Modifier,compact:Boolean=false,onClick:()->Unit) {
    Button(onClick=onClick,enabled=enabled,modifier=modifier.height(if(compact)42.dp else 52.dp),shape=RoundedCornerShape(18.dp),border=if(primary)null else BorderStroke(1.dp,Color(0xFFF3C7D1)),
        colors=ButtonDefaults.buttonColors(containerColor=if(primary)Color(0xFFF04455)else Color.White,contentColor=if(primary)Color.White else RegistrationRed)) { Text(label,fontSize=if(compact)12.sp else 15.sp,fontWeight=FontWeight.Black) }
}
@Composable private fun RegistrationAgreement(value:Boolean,enabled:Boolean,label:String,change:(Boolean)->Unit) {
    Row(Modifier.padding(vertical=10.dp),verticalAlignment=Alignment.Top) { Checkbox(value,change,enabled=enabled);Text(label,fontSize=11.sp,modifier=Modifier.weight(1f).padding(top=12.dp)) }
}

@Composable private fun RegistrationDocumentPreview(data: String) {
    if (!data.startsWith("data:image/")) return
    val image by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, data) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val bytes=android.util.Base64.decode(data.substringAfter(','),android.util.Base64.DEFAULT)
                val bounds=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
                android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                var sample=1
                while(maxOf(bounds.outWidth,bounds.outHeight)/sample>560)sample*=2
                android.graphics.BitmapFactory.decodeByteArray(bytes,0,bytes.size,
                    android.graphics.BitmapFactory.Options().apply { inSampleSize=sample })?.asImageBitmap()
            }.getOrNull()
        }
    }
    image?.let { bitmap ->
        androidx.compose.foundation.Image(bitmap,null,modifier=Modifier.padding(vertical=10.dp)
            .widthIn(max=280.dp).heightIn(max=220.dp).fillMaxWidth()
            .aspectRatio(bitmap.width.toFloat()/bitmap.height).clip(RoundedCornerShape(16.dp)),
            contentScale=androidx.compose.ui.layout.ContentScale.Crop)
    }
}
