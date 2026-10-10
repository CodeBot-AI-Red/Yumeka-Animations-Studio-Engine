package com.yumeka.anime.engine.episode

import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.yumeka.anime.engine.R
import com.yumeka.anime.engine.ai.AiFrameFragment
import com.yumeka.anime.engine.studio.SC
import com.yumeka.anime.engine.studio.dp
import com.yumeka.anime.engine.studio.label
import com.yumeka.anime.engine.studio.pill
import com.yumeka.anime.engine.studio.roundBg
import com.yumeka.anime.engine.studio.toolButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Editor visual de episodios (estilo editor de video, identidade Yumeka).
 * Tudo funciona offline; os dados ficam na pasta do projeto.
 */
class EpisodeEditorFragment : Fragment() {

    companion object {
        private const val ARG_NAME = "name"
        private const val ARG_PATH = "path"
        const val RESULT_AI_FRAME = "yumeka_ai_frame"

        fun newInstance(name: String, path: String) = EpisodeEditorFragment().apply {
            arguments = Bundle().apply { putString(ARG_NAME, name); putString(ARG_PATH, path) }
        }

        fun projectDir(ctx: android.content.Context, name: String, path: String) =
            if (path.isNotBlank()) File(path) else File(ctx.filesDir, "projetos/$name")

        /** Insere um quadro gerado na posicao do cursor, criando uma faixa propria. */
        fun insertFrame(ep: Episode, file: File, meta: FrameMeta, durationMs: Long = 3000): Clip {
            val at = (ep.playheadMs - ep.introMs).coerceIn(0, ep.bodyMs)
            val c = Clip(ep.newId(), TrackType.IMAGE, at, durationMs, "Quadro IA", file.absolutePath, meta = meta)
            ep.clips.add(c)
            ep.playheadMs = at + ep.introMs
            return c
        }
    }

    private lateinit var storage: EpisodeStorage
    private lateinit var ep: Episode
    private val history = EpisodeHistory()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var preview: EpisodePreviewView
    private lateinit var timeline: EpisodeTimelineView
    private lateinit var timeText: TextView
    private lateinit var playBtn: TextView
    private lateinit var inspector: LinearLayout
    private lateinit var undoBtn: View
    private lateinit var redoBtn: View
    private lateinit var savedText: TextView

    private var selectedId = -1L
    private var playing = false
    private var lastTick = 0L
    private var dirty = false
    private val players = HashMap<Long, MediaPlayer>()
    private val spoken = HashSet<Long>()
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private var pendingPick: ((Uri) -> Unit)? = null
    private val picker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val cb = pendingPick; pendingPick = null
        if (uri != null && cb != null) cb(uri)
    }

    // ── Ciclo de vida ────────────────────────────────────────────────────────

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        val ctx = requireContext()
        val name = arguments?.getString(ARG_NAME) ?: "Projeto"
        val path = arguments?.getString(ARG_PATH).orEmpty()
        storage = EpisodeStorage(projectDir(ctx, name, path))
        ep = storage.load(name)
        tts = TextToSpeech(ctx) { st ->
            ttsReady = st == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale("pt", "BR")
        }
        parentFragmentManager.setFragmentResultListener(RESULT_AI_FRAME, this) { _, b ->
            val f = File(b.getString("file").orEmpty())
            val meta = runCatching { FrameMeta.fromJson(JSONObject(b.getString("meta").orEmpty())) }.getOrNull()
            if (f.exists() && meta != null) {
                commit { val c = insertFrame(ep, f, meta); selectedId = c.id }
                toast("Quadro adicionado na posicao do cursor")
            }
        }
        return buildUi()
    }

    override fun onResume() {
        super.onResume()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    override fun onPause() {
        super.onPause()
        stop()
        if (dirty) save(silent = true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        releasePlayers()
        tts?.shutdown(); tts = null
        preview.release()
    }

    // ── Interface ────────────────────────────────────────────────────────────

    private fun buildUi(): View {
        val ctx = requireContext()
        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(SC.BG) }

        // Barra superior
        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(6), ctx.dp(6), ctx.dp(6), ctx.dp(6)); setBackgroundColor(SC.PANEL)
        }
        top.addView(ctx.toolButton("←", null, false, 44) { activity?.onBackPressedDispatcher?.onBackPressed() })
        val titles = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(ctx.dp(8), 0, ctx.dp(8), 0) }
        titles.addView(ctx.label("EDITAR EPISODIO", 9f, SC.PINK, true).apply { letterSpacing = .2f })
        titles.addView(ctx.label(ep.name, 15f, SC.TEXT, true).apply { maxLines = 1 })
        savedText = ctx.label("Salvo", 10f, SC.GREEN)
        titles.addView(savedText)
        top.addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val actions = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        undoBtn = ctx.toolButton("↶", null, false, 44) { undo() }
        redoBtn = ctx.toolButton("↷", null, false, 44) { redo() }
        actions.addView(undoBtn); actions.addView(redoBtn)
        actions.addView(ctx.toolButton("💾", null, false, 44) { save(silent = false) })
        actions.addView(ctx.pill("Exportar", true) { showExport() })
        root.addView(top)
        root.addView(HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(actions) })

        val body = ScrollView(ctx).apply { isFillViewport = true }
        val col = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

        // Pre-visualizacao
        val pvBox = FrameLayout(ctx).apply { setBackgroundColor(0xFF000000.toInt()) }
        preview = EpisodePreviewView(ctx).apply { episode = ep; timeMs = ep.playheadMs }
        pvBox.addView(preview, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        val maxPv = (resources.displayMetrics.heightPixels * 0.42f).toInt()
        col.addView(pvBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, maxPv))

        // Transporte
        val tr = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(8), ctx.dp(6), ctx.dp(8), ctx.dp(6))
        }
        tr.addView(ctx.toolButton("⏮", null, false, 44) { seek(0) })
        val pb = ctx.toolButton("▶", null, false, 52) { togglePlay() }
        playBtn = pb.getChildAt(0) as TextView
        tr.addView(pb)
        tr.addView(ctx.toolButton("⏭", null, false, 44) { seek(ep.totalMs) })
        timeText = ctx.label("", 13f, SC.TEXT, true).apply { setPadding(ctx.dp(10), 0, 0, 0) }
        tr.addView(timeText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(tr)
        col.addView(ctx.pill("✨ Gerar quadro com IA", true, SC.PURPLE) { openAi() }.apply {
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(48))
        })

        // Linha do tempo
        timeline = EpisodeTimelineView(ctx).apply {
            episode = ep; playheadMs = ep.playheadMs
            onPlayhead = { ms -> if (playing) stop(); applyPlayhead(ms) }
            onSelect = { id -> selectedId = id; refreshInspector() }
            onEditStart = { history.push(ep); refreshUndo() }
            onEditEnd = { markDirty(); preview.invalidate(); refreshInspector() }
        }
        col.addView(timeline)

        // Inspetor do item selecionado
        val insScroll = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false }
        inspector = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(8), ctx.dp(8), ctx.dp(8), ctx.dp(8))
        }
        insScroll.addView(inspector)
        col.addView(insScroll)
        body.addView(col)
        root.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // Barra inferior de ferramentas
        val navScroll = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; setBackgroundColor(SC.PANEL) }
        val nav = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(ctx.dp(4), ctx.dp(6), ctx.dp(4), ctx.dp(6)) }
        listOf(
            Triple("🎬", "Cena") { addScene() },
            Triple("💬", "Falas") { addDialogue() },
            Triple("🎙", "Audio") { pickAudio(TrackType.AUDIO) },
            Triple("🎵", "Musica") { pickAudio(TrackType.MUSIC) },
            Triple("🔊", "Efeito sonoro") { pickAudio(TrackType.SFX) },
            Triple("✨", "Efeito visual") { addVfx() },
            Triple("🖼", "Imagem") { pickImage() },
            Triple("📹", "Video") { pickVideo() },
            Triple("🅣", "Texto") { addText() },
            Triple("🎌", "Introducao") { editBookend(true) },
            Triple("🏁", "Desfecho") { editBookend(false) },
            Triple("↕", "Reorganizar") { reorderScenes() },
            Triple("🤖", "Quadro IA") { openAi() }
        ).forEach { (ic, cap, act) -> nav.addView(ctx.toolButton(ic, cap, false, 68) { act() }) }
        navScroll.addView(nav)
        root.addView(navScroll)

        refreshAll()
        return root
    }

    private fun refreshAll() {
        preview.episode = ep; preview.timeMs = ep.playheadMs
        timeline.episode = ep; timeline.selectedId = selectedId; timeline.playheadMs = ep.playheadMs
        updateTime(); refreshInspector(); refreshUndo()
    }

    private fun refreshUndo() {
        undoBtn.alpha = if (history.canUndo) 1f else .35f
        redoBtn.alpha = if (history.canRedo) 1f else .35f
    }

    private fun updateTime() {
        timeText.text = "${EpisodeTimelineView.fmt(ep.playheadMs)} / ${EpisodeTimelineView.fmt(ep.totalMs)}"
    }

    private fun refreshInspector() {
        val ctx = context ?: return
        inspector.removeAllViews()
        val c = ep.clip(selectedId)
        if (c == null) {
            inspector.addView(ctx.label("Toque em um item da linha do tempo para edita-lo. Arraste para mover, puxe as bordas para redimensionar.", 12f, SC.TEXT2))
            return
        }
        inspector.addView(ctx.label("${c.type.icon} ${c.label}  ·  ${EpisodeTimelineView.fmt(c.durationMs)}", 12f, SC.TEXT, true).apply { setPadding(0, 0, ctx.dp(8), 0) })
        fun act(t: String, accent: Int = SC.PINK, f: () -> Unit) =
            inspector.addView(ctx.pill(t, false, accent) { f() }.apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ctx.dp(44)).apply { rightMargin = ctx.dp(6) }
            })
        act("⏱ Duracao") { editDuration(c) }
        act("✂ Cortar") { splitSelected() }
        act("⧉ Duplicar") { duplicateSelected() }
        if (c.type.visual) {
            act("🔍 Zoom: ${zoomName(c.zoom)}") { pickZoom(c) }
            act("🌗 Transicao: ${c.transition}") { pickTransition(c) }
        }
        if (c.type == TrackType.TEXT || c.type == TrackType.DIALOGUE) act("✏ Editar texto") { editClipText(c) }
        if (c.type == TrackType.VFX) act("✨ Tipo de efeito") { pickVfxKind(c) }
        if (c.type in listOf(TrackType.IMAGE, TrackType.SCENE)) act("🔁 Substituir") { replaceImage(c) }
        if (c.type in listOf(TrackType.AUDIO, TrackType.MUSIC, TrackType.SFX)) act("🔈 Volume ${(c.volume * 100).toInt()}%") { editVolume(c) }
        act("🅣 + Texto aqui") { addText(at = c.startMs) }
        act("💬 + Fala aqui") { addDialogue(at = c.startMs) }
        act("🔊 + Som aqui") { pickAudio(TrackType.SFX, at = c.startMs) }
        if (c.meta != null) act("ℹ Metadados", SC.PURPLE) { showMeta(c) }
        act("⬆ Faixa") { moveRow(c, -1) }
        act("⬇ Faixa") { moveRow(c, 1) }
        act("🗑 Excluir", SC.RED) { confirmDelete(c) }
    }

    // ── Edicao (com desfazer) ────────────────────────────────────────────────

    private inline fun commit(block: () -> Unit) {
        history.push(ep)
        block()
        markDirty(); refreshAll()
    }

    private fun markDirty() {
        dirty = true
        if (::savedText.isInitialized) { savedText.text = "Alteracoes nao salvas"; savedText.setTextColor(SC.AMBER) }
        handler.removeCallbacks(autoSave); handler.postDelayed(autoSave, 4000)
    }

    private val autoSave = Runnable { if (dirty) save(silent = true) }

    private fun save(silent: Boolean) {
        try {
            storage.save(ep); dirty = false
            if (::savedText.isInitialized) { savedText.text = "Salvo"; savedText.setTextColor(SC.GREEN) }
            if (!silent) toast("Projeto salvo no aparelho")
        } catch (e: Exception) {
            toast("Nao foi possivel salvar: ${e.message ?: "erro de armazenamento"}")
        }
    }

    private fun undo() {
        val prev = history.undo(ep) ?: return toast("Nada para desfazer")
        ep = prev; if (ep.clip(selectedId) == null) selectedId = -1; markDirty(); refreshAll()
    }

    private fun redo() {
        val next = history.redo(ep) ?: return toast("Nada para refazer")
        ep = next; if (ep.clip(selectedId) == null) selectedId = -1; markDirty(); refreshAll()
    }

    private val bodyCursor get() = (ep.playheadMs - ep.introMs).coerceIn(0, ep.bodyMs)

    private fun addClip(type: TrackType, label: String, dur: Long, file: String = "", text: String = "", at: Long = bodyCursor) {
        commit {
            val c = Clip(ep.newId(), type, at, dur.coerceAtLeast(200), label, file, text)
            if (type == TrackType.MUSIC) c.volume = 0.6f
            ep.clips.add(c); selectedId = c.id
        }
    }

    private fun addScene() = input("Nova cena", "Nome da cena", "Cena ${ep.clips.count { it.type == TrackType.SCENE } + 1}") { n ->
        addClip(TrackType.SCENE, n, 4000)
    }

    private fun addText(at: Long = bodyCursor) = input("Texto na tela", "Digite o texto", "") { t ->
        addClip(TrackType.TEXT, t.take(24), 3000, text = t, at = at)
    }

    private fun addDialogue(at: Long = bodyCursor) {
        val ctx = requireContext()
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(ctx.dp(20), ctx.dp(8), ctx.dp(20), 0) }
        val who = EditText(ctx).apply { hint = "Personagem (opcional)" }
        val line = EditText(ctx).apply { hint = "Fala"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE }
        box.addView(who); box.addView(line)
        AlertDialog.Builder(ctx).setTitle("Adicionar fala").setView(box)
            .setPositiveButton("Adicionar") { _, _ ->
                val txt = line.text.toString().trim()
                if (txt.isEmpty()) return@setPositiveButton toast("Escreva a fala")
                val full = if (who.text.isBlank()) txt else "${who.text.trim()}: $txt"
                val dur = (1200 + txt.length * 70L).coerceIn(1500, 12000)
                addClip(TrackType.DIALOGUE, full.take(28), dur, text = full, at = at)
            }
            .setNeutralButton("Usar arquivo de voz") { _, _ ->
                pickAudio(TrackType.DIALOGUE, at, line.text.toString().trim())
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun pickAudio(type: TrackType, at: Long = bodyCursor, text: String = "") = pick("audio/*") { uri ->
        importAndAdd(uri, type, "audio") { f ->
            val dur = mediaDuration(f)
            if (dur == null) { f.delete(); toast("Arquivo de audio invalido ou nao suportado"); return@importAndAdd null }
            Clip(ep.newId(), type, at, dur, f.nameWithoutExtension.substringAfter('_').take(24), f.absolutePath, text)
        }
    }

    private fun pickImage() = pick("image/*") { uri ->
        importAndAdd(uri, TrackType.IMAGE, "img") { f ->
            if (imageSize(f) == null) { f.delete(); toast("Imagem invalida ou formato nao suportado"); null }
            else Clip(ep.newId(), TrackType.IMAGE, bodyCursor, 3000, f.nameWithoutExtension.substringAfter('_').take(24), f.absolutePath)
        }
    }

    private fun pickVideo() = pick("video/*") { uri ->
        importAndAdd(uri, TrackType.VIDEO, "vid") { f ->
            val dur = mediaDuration(f)
            if (dur == null) { f.delete(); toast("Video invalido ou nao suportado"); return@importAndAdd null }
            Clip(ep.newId(), TrackType.VIDEO, bodyCursor, dur, f.nameWithoutExtension.substringAfter('_').take(24), f.absolutePath)
        }
    }

    private fun replaceImage(c: Clip) = pick("image/*") { uri ->
        lifecycleScope.launch {
            val f = runCatching { withContext(Dispatchers.IO) { storage.importUri(requireContext(), uri, "img") } }
                .getOrElse { return@launch toast(it.message ?: "Falha ao importar") }
            if (imageSize(f) == null) { f.delete(); return@launch toast("Imagem invalida") }
            commit { c.file = f.absolutePath; c.label = f.nameWithoutExtension.substringAfter('_').take(24) }
        }
    }

    private fun importAndAdd(uri: Uri, type: TrackType, prefix: String, make: (File) -> Clip?) {
        val dlg = progressDialog("Importando ${type.label.lowercase()}...")
        lifecycleScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { storage.importUri(requireContext(), uri, prefix) } }
            dlg.dismiss()
            val f = result.getOrElse { return@launch toast(it.message ?: "Falha ao importar o arquivo") }
            val c = make(f) ?: return@launch
            commit { ep.clips.add(c); selectedId = c.id }
        }
    }

    private fun addVfx() {
        val kinds = arrayOf("flash", "escurecer", "clarear", "vinheta", "linhas de velocidade", "sakura")
        AlertDialog.Builder(requireContext()).setTitle("Efeito visual")
            .setItems(kinds) { _, i -> addClip(TrackType.VFX, kinds[i], 1500, text = kinds[i]) }.show()
    }

    private fun pickVfxKind(c: Clip) {
        val kinds = arrayOf("flash", "escurecer", "clarear", "vinheta", "linhas de velocidade", "sakura")
        AlertDialog.Builder(requireContext()).setTitle("Tipo de efeito")
            .setItems(kinds) { _, i -> commit { c.text = kinds[i]; c.label = kinds[i] } }.show()
    }

    private fun editDuration(c: Clip) = input("Duracao (segundos)", "Ex: 3.5", "%.1f".format(c.durationMs / 1000f), numeric = true) { s ->
        val v = s.replace(',', '.').toFloatOrNull()
        if (v == null || v < 0.2f || v > 3600f) toast("Informe um valor entre 0.2 e 3600 segundos")
        else commit { c.durationMs = (v * 1000).toLong() }
    }

    private fun editVolume(c: Clip) = input("Volume (0 a 100)", "Ex: 80", "${(c.volume * 100).toInt()}", numeric = true) { s ->
        val v = s.toIntOrNull()
        if (v == null || v !in 0..100) toast("Informe um valor entre 0 e 100") else commit { c.volume = v / 100f }
    }

    private fun editClipText(c: Clip) = input("Editar texto", "Texto", c.text) { t -> commit { c.text = t; c.label = t.take(24) } }

    private fun zoomName(z: String) = when (z) { "in" -> "aproximar"; "out" -> "afastar"; else -> "nenhum" }

    private fun pickZoom(c: Clip) {
        val opts = arrayOf("Nenhum", "Aproximar (zoom in)", "Afastar (zoom out)"); val vals = arrayOf("none", "in", "out")
        AlertDialog.Builder(requireContext()).setTitle("Movimento de zoom").setItems(opts) { _, i -> commit { c.zoom = vals[i] } }.show()
    }

    private fun pickTransition(c: Clip) {
        val vals = arrayOf("none", "fade")
        AlertDialog.Builder(requireContext()).setTitle("Transicao")
            .setItems(arrayOf("Nenhuma", "Esmaecer (fade)")) { _, i -> commit { c.transition = vals[i] } }.show()
    }

    private fun splitSelected() {
        val c = ep.clip(selectedId) ?: return toast("Selecione um item para cortar")
        val t = bodyCursor
        if (t <= c.startMs + 100 || t >= c.endMs - 100) return toast("Posicione o cursor dentro do item para cortar")
        commit {
            val right = c.copy(id = ep.newId(), startMs = t, durationMs = c.endMs - t, meta = c.meta?.copy())
            c.durationMs = t - c.startMs
            ep.clips.add(ep.clips.indexOf(c) + 1, right); selectedId = right.id
        }
    }

    private fun duplicateSelected() {
        val c = ep.clip(selectedId) ?: return toast("Selecione um item para duplicar")
        commit {
            val d = c.copy(id = ep.newId(), startMs = c.endMs, label = c.label, meta = c.meta?.copy())
            ep.clips.add(ep.clips.indexOf(c) + 1, d); selectedId = d.id
        }
    }

    private fun moveRow(c: Clip, dir: Int) {
        val i = ep.clips.indexOf(c); val j = i + dir
        if (j !in ep.clips.indices) return
        commit { ep.clips.removeAt(i); ep.clips.add(j, c) }
    }

    private fun confirmDelete(c: Clip) {
        AlertDialog.Builder(requireContext()).setTitle("Excluir item?")
            .setMessage("\"${c.label}\" sera removido da linha do tempo." + if (c.file.isNotBlank()) "\nO arquivo importado tambem sera apagado do projeto se nao for usado em outro lugar." else "")
            .setPositiveButton("Excluir") { _, _ ->
                val path = c.file
                commit { ep.clips.remove(c); selectedId = -1 }
                // Mantem o arquivo enquanto puder ser recuperado pelo desfazer; apaga apenas midia importada nao referenciada ao salvar
                if (path.isNotBlank() && !history.canUndo) storage.deleteMediaIfUnused(ep, path)
            }
            .setNegativeButton("Cancelar", null).show()
    }

    private fun reorderScenes() {
        val ctx = requireContext()
        val scenes = ep.clips.filter { it.type in listOf(TrackType.SCENE, TrackType.IMAGE, TrackType.VIDEO) }.sortedBy { it.startMs }.toMutableList()
        if (scenes.size < 2) return toast("Adicione pelo menos duas cenas, imagens ou videos para reorganizar")
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(ctx.dp(16), ctx.dp(8), ctx.dp(16), 0) }
        fun render() {
            box.removeAllViews()
            scenes.forEachIndexed { i, c ->
                val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, ctx.dp(4), 0, ctx.dp(4)) }
                row.addView(ctx.label("${i + 1}. ${c.type.icon} ${c.label}", 14f, SC.TEXT), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                row.addView(ctx.toolButton("⬆", null, false, 44) { if (i > 0) { scenes.add(i - 1, scenes.removeAt(i)); render() } })
                row.addView(ctx.toolButton("⬇", null, false, 44) { if (i < scenes.size - 1) { scenes.add(i + 1, scenes.removeAt(i)); render() } })
                box.addView(row)
            }
        }
        render()
        AlertDialog.Builder(ctx).setTitle("Reorganizar cenas").setView(ScrollView(ctx).apply { addView(box) })
            .setPositiveButton("Aplicar") { _, _ ->
                commit {
                    var t = scenes.minOf { it.startMs }
                    scenes.forEach { it.startMs = t; t += it.durationMs }
                }
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun editBookend(intro: Boolean) {
        val ctx = requireContext()
        val b = if (intro) ep.intro else ep.outro
        val box = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(ctx.dp(20), ctx.dp(8), ctx.dp(20), 0) }
        val enabled = android.widget.CheckBox(ctx).apply { text = if (intro) "Usar introducao" else "Usar desfecho"; isChecked = b.enabled || b.title.isEmpty() }
        val title = EditText(ctx).apply { hint = "Titulo"; setText(b.title.ifEmpty { if (intro) ep.name else "Continua..." }) }
        val sub = EditText(ctx).apply { hint = "Subtitulo (opcional)"; setText(b.subtitle) }
        val dur = EditText(ctx).apply { hint = "Duracao em segundos"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL; setText("%.1f".format(b.durationMs / 1000f)) }
        listOf(enabled, title, sub, dur).forEach { box.addView(it) }
        AlertDialog.Builder(ctx).setTitle(if (intro) "Introducao do episodio" else "Desfecho do episodio").setView(box)
            .setPositiveButton("Aplicar") { _, _ ->
                val s = dur.text.toString().replace(',', '.').toFloatOrNull()
                if (s == null || s < 0.5f || s > 120f) return@setPositiveButton toast("Duracao deve ficar entre 0.5 e 120 segundos")
                commit {
                    b.enabled = enabled.isChecked; b.title = title.text.toString().trim()
                    b.subtitle = sub.text.toString().trim(); b.durationMs = (s * 1000).toLong()
                }
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun showMeta(c: Clip) {
        val m = c.meta ?: return
        val a = m.analysis
        val txt = buildString {
            appendLine("Prompt: ${m.prompt}")
            appendLine("Negative prompt: ${m.negativePrompt.ifBlank { "-" }}")
            appendLine("Seed: ${m.seed}")
            appendLine("Modelo: ${m.model}")
            appendLine("Resolucao: ${m.width}x${m.height}  ·  Steps: ${m.steps}  ·  Guidance: ${m.guidance}")
            appendLine("Criado em: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(m.createdAt))}")
            if (a != null) { appendLine(); appendLine("Analise (Moondream2): ${a.description}"); appendLine("Problemas: ${a.problems}") }
        }
        AlertDialog.Builder(requireContext()).setTitle("Metadados do quadro").setMessage(txt).setPositiveButton("OK", null).show()
    }

    // ── Reproducao ───────────────────────────────────────────────────────────

    private fun seek(ms: Long) { stop(); applyPlayhead(ms.coerceIn(0, ep.totalMs)) }

    private fun applyPlayhead(ms: Long) {
        ep.playheadMs = ms
        preview.timeMs = ms
        timeline.playheadMs = ms
        updateTime()
    }

    private fun togglePlay() = if (playing) stop() else play()

    private fun play() {
        if (ep.totalMs <= 0) return toast("O episodio ainda esta vazio")
        if (ep.playheadMs >= ep.totalMs) applyPlayhead(0)
        playing = true; playBtn.text = "⏸"; lastTick = System.currentTimeMillis(); spoken.clear()
        handler.post(tick)
    }

    private fun stop() {
        if (!playing) return
        playing = false
        if (::playBtn.isInitialized) playBtn.text = "▶"
        handler.removeCallbacks(tick)
        releasePlayers(); tts?.stop()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!playing) return
            val now = System.currentTimeMillis()
            val t = ep.playheadMs + (now - lastTick); lastTick = now
            if (t >= ep.totalMs) { applyPlayhead(ep.totalMs); stop(); return }
            applyPlayhead(t); syncAudio(t)
            handler.postDelayed(this, 33)
        }
    }

    private fun syncAudio(t: Long) {
        val bodyT = t - ep.introMs
        for (c in ep.clips) {
            val active = bodyT >= c.startMs && bodyT < c.endMs
            val audible = c.type in listOf(TrackType.AUDIO, TrackType.MUSIC, TrackType.SFX, TrackType.DIALOGUE, TrackType.VIDEO)
            if (!audible) continue
            if (c.type == TrackType.DIALOGUE && c.file.isBlank()) {
                if (active && c.id !in spoken && ttsReady) { spoken.add(c.id); tts?.speak(c.text, TextToSpeech.QUEUE_ADD, null, "c${c.id}") }
                continue
            }
            val mp = players[c.id]
            if (active && mp == null && c.file.isNotBlank() && File(c.file).exists()) {
                runCatching {
                    MediaPlayer().apply {
                        setDataSource(c.file); prepare()
                        setVolume(c.volume, c.volume); seekTo((bodyT - c.startMs).toInt()); start()
                    }
                }.onSuccess { players[c.id] = it }
            } else if (!active && mp != null) {
                runCatching { mp.stop() }; mp.release(); players.remove(c.id)
            }
        }
    }

    private fun releasePlayers() {
        players.values.forEach { runCatching { it.stop() }; it.release() }
        players.clear()
    }

    // ── Exportacao ───────────────────────────────────────────────────────────

    private fun showExport() {
        if (ep.totalMs <= 0) return toast("Adicione conteudo antes de exportar")
        AlertDialog.Builder(requireContext()).setTitle("Exportar episodio")
            .setItems(arrayOf(
                "Sequencia de imagens PNG (12 fps, 1024x576)",
                "Pacote do projeto (.zip) para backup",
                "Video MP4 com audio (em preparacao)"
            )) { _, i ->
                when (i) {
                    0 -> exportPngs()
                    1 -> exportZip()
                    else -> AlertDialog.Builder(requireContext()).setTitle("Video MP4")
                        .setMessage("A codificacao em MP4 com mistura de audio ainda nao esta disponivel neste editor. " +
                            "Use a sequencia PNG + os arquivos de audio do pacote .zip, ou exporte pelo Yumeka Studio.")
                        .setPositiveButton("OK", null).show()
                }
            }.show()
    }

    private fun exportZip() {
        val (dlg, bar) = progressBarDialog("Gerando pacote...")
        lifecycleScope.launch {
            val r = runCatching { withContext(Dispatchers.IO) { storage.exportPackage(ep) { p -> handler.post { bar.progress = p } } } }
            dlg.dismiss()
            r.onSuccess { done("Pacote salvo em:\n${it.absolutePath}") }.onFailure { toast("Falha ao exportar: ${it.message}") }
        }
    }

    private fun exportPngs() {
        save(silent = true)
        val (dlg, bar) = progressBarDialog("Renderizando quadros...")
        val snapshot = Episode.fromJson(ep.toJson())
        lifecycleScope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    val dir = EpisodeStorage.uniqueFile(storage.exportDir, "${snapshot.name.replace(Regex("[^A-Za-z0-9_-]"), "_")}_quadros").apply { mkdirs() }
                    val renderer = EpisodeRenderer(1.5f)
                    val bmp = Bitmap.createBitmap(1024, 576, Bitmap.Config.ARGB_8888)
                    val cv = Canvas(bmp)
                    val total = snapshot.totalMs
                    val frames = (total / (1000 / 12)).toInt().coerceAtLeast(1)
                    for (i in 0 until frames) {
                        renderer.drawAt(cv, 1024, 576, snapshot, i * 1000L / 12, fastVideo = false)
                        FileOutputStream(File(dir, "quadro_%05d.png".format(i))).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                        if (i % 6 == 0) handler.post { bar.progress = i * 100 / frames }
                    }
                    renderer.release(); bmp.recycle()
                    dir
                }
            }
            dlg.dismiss()
            r.onSuccess { done("Quadros salvos em:\n${it.absolutePath}") }.onFailure { toast("Falha ao exportar: ${it.message}") }
        }
    }

    // ── IA ───────────────────────────────────────────────────────────────────

    private fun openAi() {
        stop(); save(silent = true)
        val args = arguments ?: Bundle()
        parentFragmentManager.beginTransaction()
            .add(R.id.fragment_container, AiFrameFragment.newInstance(args.getString(ARG_NAME) ?: ep.name, args.getString(ARG_PATH).orEmpty(), standalone = false), "AI_FRAME")
            .addToBackStack("ai_frame").setReorderingAllowed(true).commit()
    }

    // ── Utilitarios ──────────────────────────────────────────────────────────

    private fun pick(mime: String, cb: (Uri) -> Unit) {
        pendingPick = cb
        runCatching { picker.launch(mime) }.onFailure { pendingPick = null; toast("Nenhum seletor de arquivos disponivel") }
    }

    private fun mediaDuration(f: File): Long? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(f.absolutePath)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.takeIf { it > 0 }
        } catch (_: Exception) { null } finally { runCatching { r.release() } }
    }

    private fun imageSize(f: File): Pair<Int, Int>? {
        val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeFile(f.absolutePath, o)
        return if (o.outWidth > 0) o.outWidth to o.outHeight else null
    }

    private fun input(title: String, hint: String, value: String, numeric: Boolean = false, ok: (String) -> Unit) {
        val ctx = requireContext()
        val et = EditText(ctx).apply {
            this.hint = hint; setText(value)
            if (numeric) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val wrap = FrameLayout(ctx).apply { setPadding(ctx.dp(20), ctx.dp(8), ctx.dp(20), 0); addView(et) }
        AlertDialog.Builder(ctx).setTitle(title).setView(wrap)
            .setPositiveButton("OK") { _, _ ->
                val v = et.text.toString().trim()
                if (v.isEmpty()) toast("O campo nao pode ficar vazio") else ok(v)
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun progressDialog(msg: String): AlertDialog {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(24), ctx.dp(20), ctx.dp(24), ctx.dp(20))
            addView(ProgressBar(ctx)); addView(ctx.label(msg, 14f, 0xFF222222.toInt()).apply { setPadding(ctx.dp(16), 0, 0, 0) })
        }
        return AlertDialog.Builder(ctx).setView(row).setCancelable(false).show()
    }

    private fun progressBarDialog(msg: String): Pair<AlertDialog, ProgressBar> {
        val ctx = requireContext()
        val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(ctx.dp(24), ctx.dp(20), ctx.dp(24), ctx.dp(12))
            addView(ctx.label(msg, 14f, 0xFF222222.toInt())); addView(bar)
        }
        return AlertDialog.Builder(ctx).setView(col).setCancelable(false).show() to bar
    }

    private fun done(msg: String) {
        AlertDialog.Builder(requireContext()).setTitle("Exportacao concluida").setMessage(msg).setPositiveButton("OK", null).show()
    }

    private fun toast(msg: String) { context?.let { Toast.makeText(it, msg, Toast.LENGTH_LONG).show() } }
}
