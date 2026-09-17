package com.yumeka.anime.engine.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yumeka.anime.engine.R

/**
 * EditorFragment — Editor principal do YASE.
 *
 * Conceitos separados (nao confundir entre si):
 *   Cena     = secao/parte do episodio (gerenciada logicamente pelo editor)
 *   Frame    = quadro pertencente a Cena selecionada
 *   Keyframe = ponto de animacao dentro de um Frame
 *
 * A estrutura fisica de pastas (Quadros/, 3D Scene/, Efeitos/, Audio/, Scripts/)
 * foi criada pelo ProjectCreator e nao e reproduzida aqui como categorias visuais.
 */
class EditorFragment : Fragment() {

    private var projectName: String = "Projeto sem titulo"

    companion object {
        private const val ARG_PROJECT_NAME = "project_name"

        fun newInstance(projectName: String): EditorFragment {
            return EditorFragment().apply {
                arguments = Bundle().also { it.putString(ARG_PROJECT_NAME, projectName) }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Dados em memoria: Cenas e Frames (nao sao pastas fisicas)
    // -------------------------------------------------------------------------

    /** Modelo logico de uma Cena do episodio */
    data class Cena(
        val id: Int,
        val nome: String,
        val inicio: String,
        val fim: String
    )

    /** Modelo logico de um Frame pertencente a uma Cena */
    data class Frame(
        val id: Int,
        val nome: String,
        val inicio: String,
        val fim: String
    )

    // Estado do editor
    private val cenas = mutableListOf(
        Cena(1, "Cena 1", "00:00", "00:15"),
        Cena(2, "Cena 2", "00:15", "00:30"),
        Cena(3, "Cena 3", "00:30", "00:45"),
        Cena(4, "Cena 4", "00:45", "01:00")
    )

    private var cenaSelecionada: Cena = cenas.first()

    private val framesPorCena: MutableMap<Int, MutableList<Frame>> = mutableMapOf(
        1 to mutableListOf(
            Frame(1, "Frame 1", "0:00", "0:03"),
            Frame(2, "Frame 2", "0:03", "0:06"),
            Frame(3, "Frame 3", "0:06", "0:09"),
            Frame(4, "Frame 4", "0:09", "0:12"),
            Frame(5, "Frame 5", "0:12", "0:15")
        )
    )

    private var listScenes: RecyclerView? = null
    private var listFrames: RecyclerView? = null

    // -------------------------------------------------------------------------
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectName = arguments?.getString(ARG_PROJECT_NAME) ?: "Projeto sem titulo"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_editor, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Topbar
        view.findViewById<TextView>(R.id.txt_project_name)?.text = projectName
        view.findViewById<TextView>(R.id.txt_season_episode)?.text = "Temporada 1 • Episodio 1"

        view.findViewById<TextView>(R.id.btn_editor_back)?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Tabs Quadros / 3D
        view.findViewById<TextView>(R.id.tab_quadros)?.setOnClickListener {
            switchTab(view, isQuadros = true)
        }
        view.findViewById<TextView>(R.id.tab_3d)?.setOnClickListener {
            switchTab(view, isQuadros = false)
        }

        // Play
        view.findViewById<TextView>(R.id.btn_play)?.setOnClickListener {
            Toast.makeText(context, "Reproduzindo episodio...", Toast.LENGTH_SHORT).show()
        }

        // Alinhar com IA
        view.findViewById<TextView>(R.id.btn_alinhar_ia)?.setOnClickListener {
            Toast.makeText(context, "Alinhar quadro com IA — em breve", Toast.LENGTH_SHORT).show()
        }

        // Editar Video (area separada de edicao de video/timeline)
        view.findViewById<TextView>(R.id.btn_editar_video)?.setOnClickListener {
            Toast.makeText(context, "Editor de Video — em breve", Toast.LENGTH_SHORT).show()
        }

        // Keyframes
        view.findViewById<TextView>(R.id.btn_add_keyframe)?.setOnClickListener {
            Toast.makeText(context, "Adicionar keyframe — em breve", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<TextView>(R.id.btn_del_keyframe)?.setOnClickListener {
            Toast.makeText(context, "Excluir keyframe — em breve", Toast.LENGTH_SHORT).show()
        }
        view.findViewById<TextView>(R.id.btn_choose_keyframe)?.setOnClickListener {
            Toast.makeText(context, "Escolher keyframes — em breve", Toast.LENGTH_SHORT).show()
        }

        // + Frame
        view.findViewById<TextView>(R.id.btn_add_frame)?.setOnClickListener {
            adicionarFrame()
        }

        // + Cena
        view.findViewById<TextView>(R.id.btn_add_scene)?.setOnClickListener {
            adicionarCena()
        }

        // Ferramentas
        setupTools(view)

        // Camadas — nomes fixos por enquanto
        setupLayerNames(view)

        // Listas
        listScenes = view.findViewById(R.id.list_scenes)
        listScenes?.layoutManager = LinearLayoutManager(requireContext())

        listFrames = view.findViewById(R.id.list_frames)
        listFrames?.layoutManager = LinearLayoutManager(requireContext())

        refreshScenes()
        refreshFrames()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listScenes = null
        listFrames = null
    }

    // -------------------------------------------------------------------------
    // Tabs Quadros / 3D
    // -------------------------------------------------------------------------

    private fun switchTab(root: View, isQuadros: Boolean) {
        root.findViewById<TextView>(R.id.tab_quadros)?.apply {
            setBackgroundColor(
                if (isQuadros) 0xFF9B6DFF.toInt() else 0xFF22222E.toInt()
            )
            setTextColor(
                if (isQuadros) 0xFFFFFFFF.toInt() else 0xFFA0A0B8.toInt()
            )
        }

        root.findViewById<TextView>(R.id.tab_3d)?.apply {
            setBackgroundColor(
                if (isQuadros) 0xFF22222E.toInt() else 0xFF9B6DFF.toInt()
            )
            setTextColor(
                if (isQuadros) 0xFFA0A0B8.toInt() else 0xFFFFFFFF.toInt()
            )
        }

        val msg = if (isQuadros) "Modo Quadros ativo" else "Modo 3D ativo"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    // -------------------------------------------------------------------------
    // Ferramentas do canvas
    // -------------------------------------------------------------------------

    private fun setupTools(root: View) {
        val toolIds = listOf(
            R.id.tool_select, R.id.tool_move, R.id.tool_rotate,
            R.id.tool_crop, R.id.tool_brush, R.id.tool_color, R.id.tool_fill
        )
        val toolNames = listOf("Selecionar", "Mover", "Rotacionar",
            "Recortar", "Pincel (Quadro)", "Cor", "Balde")
        toolIds.forEachIndexed { i, id ->
            root.findViewById<TextView>(id)?.setOnClickListener {
                Toast.makeText(context, toolNames[i], Toast.LENGTH_SHORT).show()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Camadas — nomes e icones fixos do episodio
    // -------------------------------------------------------------------------

    private fun setupLayerNames(root: View) {
        data class LayerDef(val viewId: Int, val icon: String, val name: String)
        val layers = listOf(
            LayerDef(R.id.layer_personagem, "👤", "Personagem"),
            LayerDef(R.id.layer_3d,         "🗂",  "3D"),
            LayerDef(R.id.layer_fundo,      "🖼",  "Fundo"),
            LayerDef(R.id.layer_desenho,    "✏",         "Desenho"),
            LayerDef(R.id.layer_efeito,     "✨",         "Efeito"),
            LayerDef(R.id.layer_texto,      "T",              "Texto"),
            LayerDef(R.id.layer_audio,      "🎵",  "Audio")
        )
        layers.forEach { def ->
            val row = root.findViewById<View>(def.viewId) ?: return@forEach
            row.findViewById<TextView>(R.id.layer_icon)?.text  = def.icon
            row.findViewById<TextView>(R.id.layer_name)?.text  = def.name
            row.setOnClickListener {
                Toast.makeText(context, "Camada: ${def.name}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Cenas
    // -------------------------------------------------------------------------

    private fun adicionarCena() {
        val novaId   = (cenas.maxOfOrNull { it.id } ?: 0) + 1
        val inicioPrev = cenas.lastOrNull()?.fim ?: "00:00"
        val fimNovo   = proximoTempo(inicioPrev)
        cenas.add(Cena(novaId, "Cena $novaId", inicioPrev, fimNovo))
        refreshScenes()
        Toast.makeText(context, "Cena $novaId adicionada", Toast.LENGTH_SHORT).show()
    }

    private fun refreshScenes() {
        listScenes?.adapter = SceneAdapter(cenas) { cena ->
            cenaSelecionada = cena
            framesPorCena.getOrPut(cena.id) { mutableListOf() }
            refreshFrames()
            Toast.makeText(context, "Cena selecionada: ${cena.nome}", Toast.LENGTH_SHORT).show()
        }
    }

    // -------------------------------------------------------------------------
    // Frames
    // -------------------------------------------------------------------------

    private fun adicionarFrame() {
        val frames  = framesPorCena.getOrPut(cenaSelecionada.id) { mutableListOf() }
        val novaId  = (frames.maxOfOrNull { it.id } ?: 0) + 1
        val inicioPrev = frames.lastOrNull()?.fim ?: "0:00"
        val fimNovo    = proximoTempoFrame(inicioPrev)
        frames.add(Frame(novaId, "Frame $novaId", inicioPrev, fimNovo))
        refreshFrames()
        Toast.makeText(context, "Frame $novaId adicionado", Toast.LENGTH_SHORT).show()
    }

    private fun refreshFrames() {
        val frames = framesPorCena.getOrPut(cenaSelecionada.id) { mutableListOf() }
        listFrames?.adapter = FrameAdapter(frames) { frame ->
            Toast.makeText(context, "Frame selecionado: ${frame.nome}", Toast.LENGTH_SHORT).show()
        }
    }

    // -------------------------------------------------------------------------
    // Utilidades de tempo (simples para prototipo)
    // -------------------------------------------------------------------------

    private fun proximoTempo(atual: String): String {
        return try {
            val parts   = atual.split(":")
            var min     = parts[0].toInt()
            var sec     = parts[1].toInt() + 15
            if (sec >= 60) { sec -= 60; min++ }
            "%02d:%02d".format(min, sec)
        } catch (_: Exception) { "00:15" }
    }

    private fun proximoTempoFrame(atual: String): String {
        return try {
            val parts = atual.split(":")
            var min   = parts[0].toInt()
            var sec   = parts[1].toInt() + 3
            if (sec >= 60) { sec -= 60; min++ }
            "%d:%02d".format(min, sec)
        } catch (_: Exception) { "0:03" }
    }

    // =========================================================================
    // Adapters internos para Cenas e Frames
    // =========================================================================

    inner class SceneAdapter(
        private val items: List<Cena>,
        private val onClick: (Cena) -> Unit
    ) : RecyclerView.Adapter<SceneAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView? = v.findViewById(R.id.scene_name)
            val time: TextView? = v.findViewById(R.id.scene_time)
            val menu: TextView? = v.findViewById(R.id.scene_menu)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_scene_row, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, pos: Int) {
            val cena = items[pos]
            holder.name?.text = cena.nome
            holder.time?.text = "${cena.inicio} - ${cena.fim}"
            holder.itemView.setOnClickListener { onClick(cena) }
            holder.menu?.setOnClickListener {
                Toast.makeText(context, "Opcoes de ${cena.nome}", Toast.LENGTH_SHORT).show()
            }
            // Destaca a cena selecionada
            holder.itemView.setBackgroundColor(
                if (cena.id == cenaSelecionada.id) 0xFF1E1E3A.toInt()
                else android.graphics.Color.TRANSPARENT
            )
        }

        override fun getItemCount() = items.size
    }

    inner class FrameAdapter(
        private val items: List<Frame>,
        private val onClick: (Frame) -> Unit
    ) : RecyclerView.Adapter<FrameAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val name: TextView? = v.findViewById(R.id.frame_name)
            val time: TextView? = v.findViewById(R.id.frame_time)
            val menu: TextView? = v.findViewById(R.id.frame_menu)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_frame_row, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, pos: Int) {
            val frame = items[pos]
            holder.name?.text = frame.nome
            holder.time?.text = "${frame.inicio} - ${frame.fim}"
            holder.itemView.setOnClickListener { onClick(frame) }
            holder.menu?.setOnClickListener {
                Toast.makeText(context, "Opcoes de ${frame.nome}", Toast.LENGTH_SHORT).show()
            }
        }

        override fun getItemCount() = items.size
    }
}
