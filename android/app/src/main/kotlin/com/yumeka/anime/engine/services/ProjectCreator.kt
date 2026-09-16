package com.yumeka.anime.engine.services

import java.io.File

/**
 * YASE ProjectCreator
 * Cria a estrutura completa de pastas e arquivos
 * para um novo projeto de anime no YASE.
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
     * Retorna ALREADY_EXISTS se ja houver um projeto com esse nome.
     */
    fun createProject(baseDir: File, animeName: String): CreationResult {
        val safeName = animeName.trim()

        if (safeName.isEmpty()) {
            return CreationResult(Result.ERROR, message = "O nome nao pode estar vazio.")
        }

        val projectDir = File(baseDir, safeName)

        // Verifica duplicado
        if (projectDir.exists()) {
            return CreationResult(
                Result.ALREADY_EXISTS,
                message = "Ja existe um projeto chamado \"$safeName\". Por favor Escolha outro nome."
            )
        }

        return try {
            // 1 - Pasta raiz do projeto
            projectDir.mkdirs()

            // 2 - Plugins
            File(projectDir, "Plugins").mkdir()

            // 3 - Temporadas > Temporada 1 > Episodios > EP-1
  val ep1Dir = File(projectDir, "Temporadas/Temporada 1/Episodios/EP-1")
            ep1Dir.mkdirs()
            File(ep1Dir, "3D").mkdir()
            File(ep1Dir, "Sounds").mkdir()
            File(ep1Dir, "Scripts").mkdir()
            File(ep1Dir, "Designs").mkdir()
            File(ep1Dir, "Speeches").mkdir()

            // 4 - Icon.png (arquivo vazio placeholder)
            val iconFile = File(projectDir, "Icon.png")
            if (!iconFile.exists()) iconFile.createNewFile()

            // 5 - yase.project (arquivo de configuracao)
            val projectFile = File(projectDir, "yase.project")
            projectFile.writeText(
                buildYaseConfig(safeName)
            )

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
