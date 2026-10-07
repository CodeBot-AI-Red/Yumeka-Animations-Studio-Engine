package com.yumeka.anime.engine.ai

import android.app.AlertDialog
import android.content.ContentValues
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.yumeka.anime.engine.episode.EpisodeEditorFragment
import com.yumeka.anime.engine.episode.EpisodeStorage
import com.yumeka.anime.engine.episode.FrameAnalysis
import com.yumeka.anime.engine.episode.FrameMeta
import com.yumeka.anime.engine.studio.SC
import com.yumeka.anime.engine.studio.dp
import com.yumeka.anime.engine.studio.flow
import com.yumeka.anime.engine.studio.label
import com.yumeka.anime.engine.studio.pill
import com.yumeka.anime.engine.studio.roundBg
import com.yumeka.anime.engine.studio.sectionTitle
import com.yumeka.anime.engine.studio.toolButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.random.Random

/**
 * Painel "Gerar quadro com IA": geracao local (DreamShaper XL v2 Turbo GGUF)
 * + analise local opcional (Moondream2). Nenhuma API paga e usada.
 */
class AiFrameFragment : Fragment() {

    companion object {
        private const val ARG_NAME = "name"
        private const val ARG_PATH = "path"
        private const val ARG_STANDALONE = "standalone"
        fun newInstance(name: String, path: String, standalone: Boolean) = AiFrameFragment().apply {
            arguments = Bundle().apply { putString(ARG_NAME, name); putString(ARG_PATH, path); putBoolean(ARG_STANDALONE, standalone) }
        }
        val STYLES = listOf("Nenhum", "anime", "manga", "cel shading", "aquarela", "ghibli-like soft", "cinematic", "pixel art", "3D render")
        val RATIOS = listOf("1:1" to (1 to 1), "16:9" to (16 to 9), "9:16" to (9 to 16), "4:3" to (4 to 3), "3:4" to (3 to 4))
        val RESOLUTIONS = listOf(512, 640, 768, 1024)
        const val SAFE_MAX_RES = 768
        const val SAFE_MAX_STEPS = 6
    }

    private class Item(val file: File, var meta: FrameMeta, var thumb: Bitmap)

    private lateinit var mm: ModelManager
    private lateinit var storage: EpisodeStorage
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var promptEt: EditText
    private lateinit var negEt: EditText
    private lateinit var progressBar: ProgressBar
    private lateinit var progressText: TextView
    private lateinit var gallery: LinearLayout
    private lateinit var modelsBox: LinearLayout

    private var style = "anime"
    private var ratio = 0
    private var resolution = 512
    private var count = 1
    private var steps = 4
    private var guidance = 2f
    private var fixedSeed: Long? = null
    private var advancedAccepted = false

    private val states = HashMap<String, ModelState>()
    private val dlProgress = HashMap<String, Pair<Long, Long>>()
    private val dlJobs = HashMap<String, Job>()
    private var busy = false
    private val items = mutableListOf<Item>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, saved: Bundle?): View {
        val ctx = requireContext()
        mm = ModelManager(ctx.applicationContext)
        storage = EpisodeStorage(EpisodeEditorFragment.projectDir(ctx, arguments?.getString(ARG_NAME) ?: "Projeto", arguments?.getString(ARG_PATH).orEmpty()))
        listOf(Models.DREAMSHAPER, Models.MOONDREAM2).forEach { states[it.id] = if (mm.isInstalled(it)) ModelState.INSTALLED else ModelState.NOT_INSTALLED }

        val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(SC.BG); isClickable = true }
        val top = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(ctx.dp(6), ctx.dp(6), ctx.dp(6), ctx.dp(6)); setBackgroundColor(SC.PANEL)
        }
        top.addView(ctx.toolButton("←", null, false, 44) { close() })
        top.addView(ctx.label("Gerar quadro com IA", 16f, SC.TEXT, true).apply { setPadding(ctx.dp(8), 0, 0, 0) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(top)

        scroll = ScrollView(ctx)
        content = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(ctx.dp(14), ctx.dp(10), ctx.dp(14), ctx.dp(40)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        build()
        loadSavedFrames()
        return root
    }

    override fun onResume() { super.onResume(); activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER }

    /** Libera a memoria dos modelos ao sair do painel. */
    private fun errorTitle(t: Throwable, fallback: String) = when (t) {
        is LocalAiUnavailable -> "Recurso nao compativel"
        is NativeAiException -> t.kind.title
        is OutOfMemoryError -> "Memoria insuficiente"
        else -> fallback
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dlJobs.values.forEach { it.cancel() }; mm.cancel()
        AiSession.cancelAll() // interrompe a tarefa nativa para o lock ser liberado logo
        Thread { AiSession.releaseAll() }.start()
    }

    private fun close() { activity?.onBackPressedDispatcher?.onBackPressed() }

    // ── Montagem ─────────────────────────────────────────────────────────────

    private fun build() {
        val ctx = requireContext()
        content.removeAllViews()

        content.addView(ctx.sectionTitle("Modelos no aparelho"))
        modelsBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        content.addView(modelsBox)
        refreshModels()

        content.addView(ctx.sectionTitle("Prompt"))
        promptEt = EditText(ctx).apply {
            hint = "Descreva o quadro (ex: garota de cabelo rosa sob cerejeiras, por do sol)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; minLines = 2
            setTextColor(SC.TEXT); setHintTextColor(SC.MUTED); background = roundBg(SC.CARD, ctx.dp(12).toFloat())
            setPadding(ctx.dp(12), ctx.dp(10), ctx.dp(12), ctx.dp(10))
        }
        content.addView(promptEt)
        content.addView(ctx.sectionTitle("Negative prompt (opcional)"))
        negEt = EditText(ctx).apply {
            hint = "ex: maos deformadas, borrado, texto"; setTextColor(SC.TEXT); setHintTextColor(SC.MUTED)
            background = roundBg(SC.CARD, ctx.dp(12).toFloat()); setPadding(ctx.dp(12), ctx.dp(10), ctx.dp(12), ctx.dp(10))
        }
        content.addView(negEt)

        content.addView(ctx.sectionTitle("Estilo visual"))
        content.addView(ctx.flow(*STYLES.map { s -> ctx.pill(s, s == style) { style = s; build() } }.toTypedArray()))
        content.addView(ctx.sectionTitle("Proporcao"))
        content.addView(ctx.flow(*RATIOS.mapIndexed { i, r -> ctx.pill(r.first, i == ratio) { ratio = i; build() } }.toTypedArray()))
        content.addView(ctx.sectionTitle("Resolucao (lado maior)"))
        content.addView(ctx.flow(*RESOLUTIONS.map { r ->
            ctx.pill(if (r > SAFE_MAX_RES) "$r ⚠" else "$r", r == resolution, if (r > SAFE_MAX_RES) SC.AMBER else SC.PINK) {
                guarded(r > SAFE_MAX_RES, "Resolucao acima de ${SAFE_MAX_RES}x$SAFE_MAX_RES usa muito mais memoria e pode travar ou aquecer o aparelho.") { resolution = r; build() }
            }
        }.toTypedArray()))
        val (w, h) = size()
        content.addView(ctx.label("Tamanho final: ${w}x$h", 11f, SC.TEXT2))

        content.addView(ctx.sectionTitle("Quantidade de imagens"))
        content.addView(ctx.flow(*(1..4).map { n ->
            ctx.pill(if (n > 1) "$n ⚠" else "$n", n == count, if (n > 1) SC.AMBER else SC.PINK) {
                guarded(n > 1, "Gerar mais de uma imagem por vez multiplica o tempo e o aquecimento. As imagens serao geradas uma de cada vez.") { count = n; build() }
            }
        }.toTypedArray()))
        content.addView(ctx.sectionTitle("Avancado"))
        content.addView(ctx.flow(*(1..8).map { n ->
            ctx.pill("$n steps" + if (n > SAFE_MAX_STEPS) " ⚠" else "", n == steps, if (n > SAFE_MAX_STEPS) SC.AMBER else SC.PINK) {
                guarded(n > SAFE_MAX_STEPS, "Mais de $SAFE_MAX_STEPS steps deixa a geracao muito mais lenta no celular.") { steps = n; build() }
            }
        }.toTypedArray()))
        content.addView(ctx.flow(
            ctx.pill("Guidance: ${"%.1f".format(guidance)}", false) { askNumber("Guidance scale", guidance.toString()) { v -> guidance = v.coerceIn(0f, 10f); build() } },
            ctx.pill(fixedSeed?.let { "Seed: $it" } ?: "Seed: aleatorio", false) {
                askNumber("Seed (vazio = aleatorio)", fixedSeed?.toString() ?: "", allowEmpty = true) { v -> fixedSeed = if (v < 0) null else v.toLong(); build() }
            },
            ctx.pill("Execucao: CPU ARM64 (pesos quantizados GGUF)", false) {},
            ctx.pill("1 geracao por vez · 1 modelo carregado", false) {}
        ))

        val gen = ctx.pill(if (busy) "Cancelar" else "Gerar", true) { if (busy) { AiSession.cancelAll(); toast("Cancelando...") } else generate(null) }.apply {
            textSize = 17f; gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(56)).apply { topMargin = ctx.dp(16) }
        }
        content.addView(gen)
        progressBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; visibility = View.GONE }
        progressText = ctx.label("", 12f, SC.TEXT2)
        content.addView(progressBar); content.addView(progressText)

        content.addView(ctx.sectionTitle("Resultados"))
        gallery = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        content.addView(gallery)
        refreshGallery()
    }

    private fun size(): Pair<Int, Int> {
        val (rx, ry) = RATIOS[ratio].second
        fun r64(v: Int) = ((v + 32) / 64 * 64).coerceAtLeast(384)
        return if (rx >= ry) resolution to r64(resolution * ry / rx) else r64(resolution * rx / ry) to resolution
    }

    private fun guarded(risky: Boolean, msg: String, apply: () -> Unit) {
        if (!risky || advancedAccepted) return apply()
        AlertDialog.Builder(requireContext()).setTitle("Configuracao avancada")
            .setMessage("$msg\n\nValores seguros para Moto Edge 30 Neo: 512x512 (max. 768), 4 steps, guidance 2, 1 imagem. O DreamShaper XL e um modelo SDXL: abaixo de 512 px ele so produz borroes.")
            .setPositiveButton("Entendi, permitir") { _, _ -> advancedAccepted = true; apply() }
            .setNegativeButton("Manter seguro", null).show()
    }

    // ── Modelos ──────────────────────────────────────────────────────────────

    private fun runtimeFor(spec: ModelSpec) = if (spec === Models.DREAMSHAPER) NativeDiffusion.available else NativeVision.available

    private fun refreshModels() {
        val ctx = context ?: return
        modelsBox.removeAllViews()
        for (spec in listOf(Models.DREAMSHAPER, Models.MOONDREAM2)) {
            val st = states[spec.id] ?: ModelState.NOT_INSTALLED
            val card = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL; background = roundBg(SC.CARD, ctx.dp(14).toFloat(), ctx.dp(1), SC.BORDER)
                setPadding(ctx.dp(12), ctx.dp(10), ctx.dp(12), ctx.dp(10))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ctx.dp(8) }
            }
            card.addView(ctx.label(spec.displayName, 14f, SC.TEXT, true))
            card.addView(ctx.label("${spec.description}  ·  ${fmtBytes(spec.totalBytes)}", 11f, SC.TEXT2))
            val color = when (st) { ModelState.READY, ModelState.INSTALLED -> SC.GREEN; ModelState.ERROR -> SC.RED; ModelState.NOT_INSTALLED -> SC.MUTED; else -> SC.AMBER }
            card.addView(ctx.label("● ${st.label}", 12f, color, true))
            if (!runtimeFor(spec)) card.addView(ctx.label(
                "Motor local (${spec.runtime}) nao incluido nesta versao do app: o modelo pode ser baixado, mas ainda nao pode ser executado.", 11f, SC.AMBER))
            dlProgress[spec.id]?.let { (done, total) ->
                if (st == ModelState.DOWNLOADING || st == ModelState.INSTALLING) {
                    card.addView(ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                        max = 1000; progress = if (total > 0) (done * 1000 / total).toInt() else 0
                    })
                    card.addView(ctx.label(if (st == ModelState.INSTALLING) "Verificando e instalando..." else "${fmtBytes(done)} de ${fmtBytes(total)}", 11f, SC.TEXT2))
                }
            }
            val actions = when (st) {
                ModelState.DOWNLOADING, ModelState.INSTALLING -> listOf(ctx.pill("Cancelar", false, SC.RED) { cancelInstall(spec) })
                ModelState.INSTALLED, ModelState.READY, ModelState.LOADING -> listOf(
                    ctx.pill("Verificar compatibilidade", false) { showCompat(spec, install = false) },
                    ctx.pill("Excluir modelo", false, SC.RED) { confirmDeleteModel(spec) })
                else -> listOf(ctx.pill(if (mm.downloadedBytes(spec) > 0) "Continuar instalacao" else "Instalar", true) { showCompat(spec, install = true) })
            }
            card.addView(ctx.flow(*actions.toTypedArray()))
            modelsBox.addView(card)
        }
    }

    private fun showCompat(spec: ModelSpec, install: Boolean) {
        val rep = mm.check(spec, runtimeFor(spec))
        val lines = rep.lines(spec).joinToString("\n") { (ok, t) -> (if (ok) "✅ " else "❌ ") + t }
        val msg = buildString {
            appendLine(lines); appendLine()
            appendLine("Tamanho aproximado: ${fmtBytes(spec.totalBytes)}")
            appendLine("Espaco livre necessario: ${fmtBytes(spec.totalBytes + CompatReport.SAFETY_MARGIN)}")
            appendLine("• Todo o processamento e feito no proprio aparelho; nada e enviado para servidores.")
            appendLine("• A geracao pode aquecer o aparelho e consumir bateria.")
            appendLine("• Use Wi-Fi: o download e grande. Ele pode ser retomado se for interrompido.")
            if (!rep.runtimeAvailable) { appendLine(); appendLine(UNSUPPORTED_MESSAGE) }
            if (!CloudGeneration.enabled) appendLine("Geracao em nuvem: nao configurada neste app.")
        }
        val b = AlertDialog.Builder(requireContext()).setTitle(if (install) "Instalar ${spec.displayName}?" else "Compatibilidade").setMessage(msg)
        if (install) {
            if (rep.canInstall) b.setPositiveButton("Baixar e instalar") { _, _ -> startInstall(spec) }
            else b.setPositiveButton("Entendi", null).setTitle("Instalacao nao recomendada")
            b.setNegativeButton("Cancelar", null)
        } else b.setPositiveButton("OK", null)
        b.show()
    }

    private fun startInstall(spec: ModelSpec) {
        if (dlJobs[spec.id]?.isActive == true) return
        states[spec.id] = ModelState.DOWNLOADING; dlProgress[spec.id] = mm.downloadedBytes(spec) to spec.totalBytes; refreshModels()
        dlJobs[spec.id] = lifecycleScope.launch {
            try {
                mm.install(spec) { st, done, total ->
                    lifecycleScope.launch { states[spec.id] = st; dlProgress[spec.id] = done to total; refreshModels() }
                }
                states[spec.id] = ModelState.INSTALLED
                toast("${spec.displayName} instalado")
            } catch (t: Throwable) {
                if (ModelManager.isCancel(t)) { states[spec.id] = ModelState.NOT_INSTALLED; toast("Download cancelado. O progresso foi mantido.") }
                else { states[spec.id] = ModelState.ERROR; showError("Falha na instalacao", t.message ?: "Erro desconhecido") }
            } finally {
                dlProgress.remove(spec.id); dlJobs.remove(spec.id); refreshModels()
            }
        }
    }

    private fun cancelInstall(spec: ModelSpec) { mm.cancel(); dlJobs[spec.id]?.cancel() }

    private fun confirmDeleteModel(spec: ModelSpec) {
        AlertDialog.Builder(requireContext()).setTitle("Excluir ${spec.displayName}?")
            .setMessage("Isso libera ${fmtBytes(spec.totalBytes)}. Sera preciso baixar de novo para usar.")
            .setPositiveButton("Excluir") { _, _ ->
                lifecycleScope.launch {
                    withContext(Dispatchers.IO) { AiSession.releaseAll(); mm.delete(spec) }
                    states[spec.id] = ModelState.NOT_INSTALLED; refreshModels(); toast("Modelo excluido")
                }
            }.setNegativeButton("Cancelar", null).show()
    }

    // ── Geracao ──────────────────────────────────────────────────────────────

    private fun generate(override: FrameMeta?) {
        if (busy) return toast("Aguarde a geracao atual terminar")
        val prompt = override?.prompt ?: promptEt.text.toString().trim()
        if (prompt.isEmpty()) return toast("Escreva um prompt")
        val spec = Models.DREAMSHAPER
        if (!NativeDiffusion.available) return showError("Recurso nao compativel", unavailableMessage(NativeDiffusion.loadError) +
            if (CloudGeneration.enabled) "" else "\n\nA geracao em nuvem nao esta configurada neste app.")
        if (!mm.isInstalled(spec)) return showCompat(spec, install = true)
        val rep = mm.check(spec, true)
        if (!rep.ramOk) return showError("Memoria insuficiente", "Este aparelho nao tem memoria suficiente para executar o modelo localmente.")

        val (w, h) = if (override != null) override.width to override.height else size()
        val neg = override?.negativePrompt ?: negEt.text.toString().trim()
        val n = if (override != null) 1 else count
        busy = true; build()
        progressBar.visibility = View.VISIBLE; progressBar.progress = 0
        lifecycleScope.launch {
            try {
                for (k in 0 until n) {
                    val seed = if (override == null) fixedSeed?.plus(k) ?: Random.nextLong(0, Int.MAX_VALUE.toLong()) else Random.nextLong(0, Int.MAX_VALUE.toLong())
                    val params = GenParams(prompt, neg, if (override != null) "" else style, w, h, steps, guidance, seed)
                    setProgress(0, "Imagem ${k + 1}/$n · preparando...")
                    val bmp = withContext(Dispatchers.Default) {
                        AiSession.generate(mm, params,
                            onState = { st -> lifecycleScope.launch { states[spec.id] = st; refreshModels(); setProgress(progressBar.progress, "Imagem ${k + 1}/$n · ${st.label}") } },
                            onStep = { s, t -> lifecycleScope.launch { setProgress(s * 100 / t.coerceAtLeast(1), "Imagem ${k + 1}/$n · passo $s de $t") } })
                    }
                    val meta = FrameMeta(prompt = params.prompt + if (params.style.isNotBlank() && params.style != "Nenhum") ", ${params.style} style" else "",
                        negativePrompt = neg, seed = seed, model = spec.displayName, width = w, height = h, steps = steps, guidance = guidance)
                    val file = withContext(Dispatchers.IO) { storage.saveFrame(bmp, meta) }
                    items.add(0, Item(file, meta, bmp))
                    refreshGallery()
                }
                setProgress(100, "Concluido")
            } catch (t: Throwable) {
                setProgress(0, "")
                if (t is NativeAiException && t.kind == NativeAiException.Kind.CANCELLED) toast("Geracao cancelada")
                else showError(errorTitle(t, "Falha na geracao"), t.message ?: "Erro desconhecido")
            } finally {
                busy = false
                states[spec.id] = if (mm.isInstalled(spec)) ModelState.INSTALLED else ModelState.NOT_INSTALLED
                if (view != null) build()
            }
        }
    }

    private fun setProgress(p: Int, text: String) {
        if (view == null) return
        progressBar.progress = p; progressText.text = text
        progressBar.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
    }

    // ── Galeria ──────────────────────────────────────────────────────────────

    private fun loadSavedFrames() {
        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                storage.framesDir.listFiles { f -> f.extension == "png" }.orEmpty().sortedByDescending { it.lastModified() }.take(30).mapNotNull { f ->
                    val metaFile = File(f.parentFile, f.nameWithoutExtension + ".json")
                    val meta = runCatching { FrameMeta.fromJson(org.json.JSONObject(metaFile.readText())) }.getOrNull() ?: return@mapNotNull null
                    val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: return@mapNotNull null
                    Item(f, meta, bmp)
                }
            }
            items.addAll(loaded); if (view != null) refreshGallery()
        }
    }

    private fun refreshGallery() {
        val ctx = context ?: return
        gallery.removeAllViews()
        if (items.isEmpty()) { gallery.addView(ctx.label("Nenhum quadro gerado ainda.", 12f, SC.MUTED)); return }
        for (it in items) gallery.addView(card(it))
    }

    private fun card(item: Item): View {
        val ctx = requireContext()
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; background = roundBg(SC.CARD, ctx.dp(14).toFloat())
            setPadding(ctx.dp(10), ctx.dp(10), ctx.dp(10), ctx.dp(10))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = ctx.dp(10) }
        }
        card.addView(ImageView(ctx).apply { setImageBitmap(item.thumb); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val m = item.meta
        card.addView(ctx.label(m.prompt, 12f, SC.TEXT).apply { maxLines = 3; setPadding(0, ctx.dp(6), 0, 0) })
        card.addView(ctx.label("${m.width}x${m.height} · seed ${m.seed} · ${m.steps} steps", 10f, SC.TEXT2))
        card.addView(ctx.pill("🔎 Analisar quadro", false, SC.PURPLE) { analyze(item) }.apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(46)).apply { topMargin = ctx.dp(6) }; gravity = Gravity.CENTER
        })
        card.addView(ctx.flow(
            ctx.pill("➕ Adicionar ao episodio", true) { insert(item) },
            ctx.pill("⬇ Baixar", false) { download(item) },
            ctx.pill("🔁 Gerar novamente", false) { generate(m.copy(analysis = null)) },
            ctx.pill("✏ Editar prompt", false) { promptEt.setText(m.prompt); negEt.setText(m.negativePrompt); scroll.smoothScrollTo(0, promptEt.top) }
        ))
        m.analysis?.let { a ->
            card.addView(ctx.sectionTitle("Analise (Moondream2)"))
            card.addView(ctx.label("Descricao: ${a.description}", 12f, SC.TEXT))
            card.addView(ctx.label("Elementos: ${a.elements}", 12f, SC.TEXT))
            card.addView(ctx.label("Possiveis erros visuais: ${a.problems}", 12f, SC.AMBER))
            card.addView(ctx.label("Qualidade estimada: ${a.quality}", 12f, SC.TEXT))
            card.addView(ctx.label("Sugestoes: ${a.suggestions}", 12f, SC.TEXT2))
            card.addView(ctx.pill("🛠 Gerar versao corrigida", true, SC.GREEN) { generateCorrected(item, a) }.apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(46)).apply { topMargin = ctx.dp(6) }; gravity = Gravity.CENTER
            })
        }
        return card
    }

    private fun analyze(item: Item) {
        if (busy) return toast("Aguarde a tarefa atual terminar")
        val spec = Models.MOONDREAM2
        if (!NativeVision.available) return showError("Recurso nao compativel", unavailableMessage(NativeVision.loadError))
        if (!mm.isInstalled(spec)) return showCompat(spec, install = true)
        busy = true; build()
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val qs = analysisQuestions(item.meta.prompt)
                setProgress(0, "Analisando: descarregando o gerador e carregando o Moondream2...")
                val ans = withContext(Dispatchers.Default) {
                    AiSession.ask(mm, item.thumb, qs,
                        onState = { st -> lifecycleScope.launch { states[spec.id] = st; refreshModels() } },
                        onAnswer = { k -> lifecycleScope.launch { setProgress(k * 100 / qs.size, "Analisando (${k}/${qs.size})...") } })
                }
                val raw = ans.getOrElse(4) { "" }
                fun section(key: String) = Regex("$key\\s*:\\s*(.*?)(?=\\n[A-Z ]{5,}:|$)", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                    .find(raw)?.groupValues?.get(1)?.trim().orEmpty()
                val a = FrameAnalysis(
                    description = ans.getOrElse(0) { "" },
                    elements = ans.getOrElse(1) { "" },
                    problems = listOf(ans.getOrElse(2) { "" }, section("PROBLEMAS")).filter { it.isNotBlank() }.joinToString("\n").ifBlank { "Nenhum problema identificado" },
                    quality = ans.getOrElse(3) { "" }.ifBlank { "Nao avaliada" },
                    suggestions = section("SUGESTOES").ifBlank { raw.take(400) },
                    correctedPrompt = section("PROMPT CORRIGIDO")
                )
                item.meta.analysis = a
                withContext(Dispatchers.IO) { storage.updateFrameMeta(item.file, item.meta) }
                setProgress(100, "Analise concluida")
                refreshGallery()
            } catch (t: Throwable) {
                setProgress(0, "")
                if (t is NativeAiException && t.kind == NativeAiException.Kind.CANCELLED) toast("Analise cancelada")
                else showError(errorTitle(t, "Falha na analise"), t.message ?: "Erro desconhecido")
            } finally {
                busy = false
                states[spec.id] = if (mm.isInstalled(spec)) ModelState.INSTALLED else ModelState.NOT_INSTALLED
                if (view != null) refreshModels()
            }
        }
    }

    /** Usa o prompt original + correcoes sugeridas. O Moondream2 ja foi descarregado antes da nova geracao. */
    private fun generateCorrected(item: Item, a: FrameAnalysis) {
        val base = item.meta.prompt
        val prompt = a.correctedPrompt.ifBlank { "$base, ${a.suggestions.take(200)}" }
        val neg = listOf(item.meta.negativePrompt, "deformed, bad anatomy, extra fingers, blurry").filter { it.isNotBlank() }.joinToString(", ")
        generate(item.meta.copy(prompt = prompt, negativePrompt = neg, analysis = null))
    }

    private fun insert(item: Item) {
        val standalone = arguments?.getBoolean(ARG_STANDALONE) ?: true
        if (standalone) {
            lifecycleScope.launch {
                val ok = runCatching {
                    withContext(Dispatchers.IO) {
                        val ep = storage.load(arguments?.getString(ARG_NAME) ?: "Episodio")
                        EpisodeEditorFragment.insertFrame(ep, item.file, item.meta); storage.save(ep)
                    }
                }
                ok.onSuccess { toast("Quadro adicionado ao episodio (abra \"Editar episodio\" para ajustar)") }
                    .onFailure { toast("Nao foi possivel adicionar: ${it.message}") }
            }
        } else {
            parentFragmentManager.setFragmentResult(EpisodeEditorFragment.RESULT_AI_FRAME,
                bundleOf("file" to item.file.absolutePath, "meta" to item.meta.toJson().toString()))
            close()
        }
    }

    private fun download(item: Item) {
        val ctx = requireContext()
        lifecycleScope.launch {
            val r = runCatching {
                withContext(Dispatchers.IO) {
                    val name = "Yumeka_${item.file.name}"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val cv = ContentValues().apply {
                            put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Yumeka")
                        }
                        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv) ?: throw IllegalStateException("Galeria indisponivel")
                        ctx.contentResolver.openOutputStream(uri)?.use { o -> item.file.inputStream().use { it.copyTo(o) } }
                        "Galeria > Pictures/Yumeka"
                    } else {
                        @Suppress("DEPRECATION")
                        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Yumeka").apply { mkdirs() }
                        val out = File(dir, name)
                        FileOutputStream(out).use { o -> item.file.inputStream().use { it.copyTo(o) } }
                        out.absolutePath
                    }
                }
            }
            r.onSuccess { toast("Quadro salvo em $it") }.onFailure { toast("Nao foi possivel baixar: ${it.message}") }
        }
    }

    // ── Utilitarios ──────────────────────────────────────────────────────────

    private fun askNumber(title: String, value: String, allowEmpty: Boolean = false, ok: (Float) -> Unit) {
        val ctx = requireContext()
        val et = EditText(ctx).apply { setText(value); inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL }
        AlertDialog.Builder(ctx).setTitle(title).setView(et)
            .setPositiveButton("OK") { _, _ ->
                val s = et.text.toString().trim().replace(',', '.')
                if (s.isEmpty() && allowEmpty) ok(-1f) else s.toFloatOrNull()?.let(ok) ?: toast("Numero invalido")
            }.setNegativeButton("Cancelar", null).show()
    }

    private fun showError(title: String, msg: String) {
        val ctx = context ?: return
        AlertDialog.Builder(ctx).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show()
    }

    private fun toast(msg: String) { context?.let { Toast.makeText(it, msg, Toast.LENGTH_LONG).show() } }
}
