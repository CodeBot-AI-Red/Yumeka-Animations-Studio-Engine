package com.yumeka.anime.engine.services

import java.io.File

/**
 * YASE ProjectCreator
 * Cria a estrutura completa de pastas e arquivos
 * para um novo projeto de anime no YASE.
 *
 * Estrutura oficial:
 * NomeAnime/
 *   Plugins/
 *   Assets/
 *     Characters/
 *     Backgrounds/
 *     Materials/
 *   Temporadas/
 *     Temporada 1/
 *       Episodios/
 *         EP-1/
 *           Quadros/
 *           3D Scene/
 *           Efeitos/
 *           Audio/
 *             Sounds/
 *             Speeches/
 *           Scripts/
 *   Icon.png
 *   yase.project
 */
object ProjectCreator {

    enum class Result {
        SUCCESS,
        ALREADY_EXISTS,
        ERROR
    }

    data class CreationResult(
        val status: Result,
        val projectDir: File? = null,
        val message: String = ""
    )

    /**
     * Cria um novo projeto YASE com a estrutura oficial.
     * Retorna ALREADY_EXISTS se já houver um projeto com esse nome —
     * nunca sobrescreve nem apaga o projeto existente.
     */
    fun createProject(baseDir: File, animeName: String): CreationResult {
        val safeName = animeName.trim()

        if (safeName.isEmpty()) {
            return CreationResult(Result.ERROR, message = "O nome nao pode estar vazio.")
        }

        val projectDir = File(baseDir, safeName)

        // Verifica duplicado — nunca sobrescreve
        if (projectDir.exists()) {
            return CreationResult(
                Result.ALREADY_EXISTS,
                message = "Ja existe um projeto chamado \"$safeName\". Por favor escolha outro nome."
            )
        }

        return try {
            // 1 - Pasta raiz do projeto
            projectDir.mkdirs()

            // 2 - Plugins
            File(projectDir, "Plugins").mkdir()

            // 3 - Assets com sub-pastas de recursos reutilizaveis
            val assetsDir = File(projectDir, "Assets")
            assetsDir.mkdirs()
            File(assetsDir, "Characters").mkdir()
            File(assetsDir, "Backgrounds").mkdir()
            File(assetsDir, "Materials").mkdir()

            // 4 - Temporadas > Temporada 1 > Episodios > EP-1
            val ep1Dir = File(projectDir, "Temporadas/Temporada 1/Episodios/EP-1")
            ep1Dir.mkdirs()

            // Sub-pastas do EP-1 — exatamente as definidas na especificacao
            File(ep1Dir, "Quadros").mkdir()           // conteudo visual dos frames 2D
            File(ep1Dir, "3D Scene").mkdir()          // recursos/cena 3D do episodio
            File(ep1Dir, "Efeitos").mkdir()           // efeitos visuais
            val audioDir = File(ep1Dir, "Audio")
            audioDir.mkdir()
            File(audioDir, "Sounds").mkdir()          // efeitos sonoros
            File(audioDir, "Speeches").mkdir()        // falas
            File(ep1Dir, "Scripts").mkdir()           // scripts do episodio

            // 5 - Icon.png (placeholder vazio)
            val iconFile = File(projectDir, "Icon.png")
            if (!iconFile.exists()) iconFile.createNewFile()

            // 6 - yase.project (unico arquivo de configuracao na raiz)
            val projectFile = File(projectDir, "yase.project")
            projectFile.writeText(buildYaseConfig(safeName))

            CreationResult(Result.SUCCESS, projectDir = projectDir)
        } catch (e: Exception) {
            // Limpa se deu erro parcial
            projectDir.deleteRecursively()
            CreationResult(Result.ERROR, message = "Erro ao criar projeto: ${e.message}")
        }
    }

    /**
     * Gera o conteudo inicial do yase.project
     */
    private fun buildYaseConfig(name: String): String {
        val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date())
        return """
# YASE Project Configuration
name=$name
yase_version=1.0
created_at=$now
seasons=1
current_season=Temporada 1
current_episode=EP-1
icon=Icon.png
        """.trimIndent()
    }
}
