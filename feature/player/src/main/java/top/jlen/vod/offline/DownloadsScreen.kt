package top.jlen.vod.offline

import android.content.Intent
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import top.jlen.vod.data.PlaySource
import top.jlen.vod.ui.SectionTitle
import top.jlen.vod.ui.appTopInsetsPadding

private val Stage.label get() = when(this) {
    Stage.QUEUED -> "待下载"; Stage.ACTIVE -> "下载中"; Stage.PAUSED -> "已暂停"; Stage.FAILED -> "下载失败"; Stage.DONE -> "已下载"
}
@Composable
fun episodeDownloadLabels(vod: String, si: Int, source: String): Map<Int,String> {
    val lib = OfflineLibrary.get(LocalContext.current)
    val flow = remember(lib,vod,si,source) { lib.entries.map { rows -> rows.filter { it.vod == vod && it.si == si && it.source == source }.associate { it.ei to it.stage.label } }.distinctUntilChanged() }
    return flow.collectAsState(emptyMap()).value
}

@Composable
fun DownloadEpisodeHeader(vod: String,title: String,si: Int,source: PlaySource,labels: Map<Int,String>) {
    val context = LocalContext.current; val lib = OfflineLibrary.get(context)
    var picking by remember(vod,si,source) { mutableStateOf(false) }; var picked by remember(picking) { mutableStateOf(emptySet<Int>()) }
    fun add(indices: Set<Int>) {
        lib.add(vod,title,source.name,si,indices.sorted().map { i -> Triple(i,source.episodes[i].name,source.episodes[i].url) })
        picking=false; Toast.makeText(context,"已加入我的下载",Toast.LENGTH_SHORT).show()
    }
    val single = source.episodes.size == 1
    val status = labels[0].takeIf { single }
    SectionTitle(title="选集播放",action=if(status != null) "${source.name} · $status" else "${source.name} · 下载") {
        if (single && status == null) add(setOf(0)) else picking=true
    }
    if(picking) AlertDialog(onDismissRequest={picking=false}, title={Text("选择下载集数")},text={
        Column {
            Text("${source.name} · 自动保存片源提供的最高画质")
            val available = source.episodes.indices.filter { it !in labels }.toSet()
            TextButton(onClick={picked=if(picked == available) emptySet() else available},enabled=available.isNotEmpty()) { Text(if(picked == available && picked.isNotEmpty()) "取消全选" else "全选可下载集数") }
            LazyColumn(Modifier.heightIn(max=360.dp)) { itemsIndexed(source.episodes) { i,e ->
                val mark = labels[i]; val toggle = { picked = if(i in picked) picked-i else picked+i }
                Row(Modifier.fillMaxWidth().clickable(enabled=mark==null,onClick=toggle).padding(vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=i in picked,onCheckedChange=null,enabled=mark==null); Spacer(Modifier.width(12.dp))
                    Text(e.name,Modifier.weight(1f)); if(mark != null) Text(mark,style=MaterialTheme.typography.labelSmall)
                }
            } }
        }
    },confirmButton={TextButton(enabled=picked.isNotEmpty(),onClick={add(picked)}) { Text("下载（${picked.size}）") }},dismissButton={TextButton(onClick={picking=false}) { Text("关闭") }})
}

@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val context = LocalContext.current; val lib = OfflineLibrary.get(context)
    val rows by lib.entries.collectAsState(); val mobile by lib.mobile.collectAsState(); val allowed by lib.allowed.collectAsState()
    var manage by remember { mutableStateOf(false) }; var selected by remember { mutableStateOf(emptySet<String>()) }
    var deleting by remember { mutableStateOf(emptySet<String>()) }; var completed by remember { mutableStateOf(rows.isNotEmpty() && rows.all { it.stage == Stage.DONE }) }
    fun size(n: Long) = Formatter.formatFileSize(context,n)
    val completedCount = rows.count { it.stage == Stage.DONE }
    val visible = rows.filter { (it.stage == Stage.DONE) == completed }
    LaunchedEffect(Unit) { lib.schedule() }
    Column(Modifier.fillMaxSize().appTopInsetsPadding().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=onBack) { Text("返回") }; Text("我的下载",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            TextButton(onClick={manage=!manage;selected=emptySet()}) { Text(if(manage) "完成" else "管理") }
        }
        Text("离线视频 ${size(rows.sumOf { it.bytes })} · 可用 ${size(lib.root.usableSpace)}",style=MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("允许移动网络下载"); Text(if(mobile) "移动网络可能产生流量费用" else "默认仅使用 Wi-Fi",style=MaterialTheme.typography.bodySmall) }
            Switch(checked=mobile,onCheckedChange=lib::setMobile)
        }
        TabRow(selectedTabIndex=if(completed) 1 else 0) {
            listOf("进行中 ${rows.size-completedCount}","已完成 $completedCount").forEachIndexed { i,label -> Tab(selected=completed==(i==1),onClick={completed=i==1;selected=emptySet()},text={Text(label)}) }
        }
        if(manage && visible.isNotEmpty()) Row {
            TextButton(onClick={selected=if(selected.size==visible.size) emptySet() else visible.map { it.id }.toSet()}) { Text(if(selected.size==visible.size) "取消全选" else "全选") }
            TextButton(enabled=selected.isNotEmpty(),onClick={deleting=selected}) { Text("删除（${selected.size}）") }
        }
        if(visible.isEmpty()) Text(if(completed) "还没有已下载的视频" else "没有进行中的任务，可在影片详情页选择集数下载。",Modifier.padding(vertical=24.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),contentPadding=PaddingValues(bottom=24.dp)) {
            visible.groupBy { it.vod }.forEach { (vod,group) ->
                item(key="title:$vod") { Text(group.first().title,Modifier.padding(top=8.dp),style=MaterialTheme.typography.titleMedium) }
                items(group,key={it.id}) { row ->
                    val toggle = { selected = if(row.id in selected) selected-row.id else selected+row.id }
                    val play = { context.startActivity(Intent(context,OfflinePlayerActivity::class.java).putExtra("id",row.id)) }
                    Card(Modifier.fillMaxWidth().clickable(enabled=manage || completed) { if(manage) toggle() else play() }) { Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically) { if(manage) Checkbox(checked=row.id in selected,onCheckedChange={toggle()}); Text("${row.episode} · ${row.source}",Modifier.weight(1f)); if(row.quality.isNotEmpty()) Text(row.quality,style=MaterialTheme.typography.labelSmall) }
                        val status = when { row.stage == Stage.FAILED -> row.error; row.stage in setOf(Stage.QUEUED,Stage.ACTIVE) && !allowed -> if(mobile) "等待网络" else "等待 Wi-Fi"; row.stage==Stage.QUEUED -> "排队中"; else -> row.stage.label }
                        Text(listOfNotNull(status,size(row.bytes),"${row.percent}%".takeIf { row.percent>=0 && !completed }).joinToString(" · "),style=MaterialTheme.typography.bodySmall)
                        if(row.stage==Stage.ACTIVE && row.percent>=0) LinearProgressIndicator(progress={ row.percent/100f },modifier=Modifier.fillMaxWidth().padding(top=6.dp))
                        if(!manage) Row {
                            if(completed) TextButton(onClick=play) { Text("离线播放") } else TextButton(onClick={lib.pauseOrResume(row)}) { Text(when(row.stage) { Stage.PAUSED -> "继续"; Stage.FAILED -> "重试"; else -> "暂停" }) }
                            TextButton(onClick={deleting=setOf(row.id)}) { Text(if(completed) "删除" else "取消下载") }
                        }
                    } }
                }
            }
        }
    }
    if(deleting.isNotEmpty()) AlertDialog(onDismissRequest={deleting=emptySet()},title={Text("删除 ${deleting.size} 集？")},text={Text("已保存的视频和未完成的下载文件都会从本机移除。")},confirmButton={TextButton(onClick={lib.remove(deleting); selected-=deleting;deleting=emptySet()}) { Text("删除") }},dismissButton={TextButton(onClick={deleting=emptySet()}) { Text("保留") }})
}
