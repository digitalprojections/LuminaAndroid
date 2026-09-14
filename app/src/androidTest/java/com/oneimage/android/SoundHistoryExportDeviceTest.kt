package com.oneimage.android

import android.provider.DocumentsContract
import androidx.test.platform.app.InstrumentationRegistry
import com.oneimage.android.api.OneImageTask
import com.oneimage.android.api.OneImageTaskResult
import com.oneimage.android.ui.shared.exportTaskResultsToFolder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SoundHistoryExportDeviceTest {
    @Test fun historyWritesReceivedNamesToTheDocumentProvider() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val permission = context.contentResolver.persistedUriPermissions.firstOrNull { it.isWritePermission }
            ?: error("Choose a history export folder once before this test.")
        val folderTree = permission.uri
        val prefix = "filename_qa_${System.currentTimeMillis()}_"
        val source = File(context.filesDir, "oneimage-results/9200ba11-4c3a-4a73-b603-cd8820a17c8a")
            .listFiles().orEmpty().filter { it.extension == "mp3" }.sortedBy { it.name }
        assertEquals(3, source.size)
        val results = source.mapIndexed { index, file ->
            OneImageTaskResult("Node 19", file.toURI().toString(), "${prefix}dragon_roar_0000${index + 1}.mp3", file.length())
        }
        val task = OneImageTask("filename-qa", "sound_effects", "completed", null, null, 100, 100,
            "dragon roar", false, 1789365358000L, true, false, results)
        assertEquals(3, exportTaskResultsToFolder(context, folderTree, task, "sound-effects"))
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folderTree, DocumentsContract.getTreeDocumentId(folderTree))
        val names = mutableListOf<String>()
        context.contentResolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)!!.use {
            while (it.moveToNext()) { val name = it.getString(0); if (name.startsWith(prefix)) names.add(name) }
        }
        assertEquals(results.map { it.filename }.sorted(), names.sorted())
    }
}
