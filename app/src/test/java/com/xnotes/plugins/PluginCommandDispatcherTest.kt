package com.xnotes.plugins

import com.xnotes.core.geometry.Pt
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginCommandDispatcherTest {
    @Test fun routesCommandsThroughNarrowHostPort() {
        val calls = mutableListOf<String>()
        val port = object : PluginCommandPort {
            override fun openScript(pkg: DeclarativePackage, at: Pt?) { calls += "script" }
            override fun openAnnotations(id: String) { calls += "annotations:$id" }
            override fun openPenMenu(at: Pt?) { calls += "pen-menu" }
            override fun requestExport(kind: ExportKind) { calls += "export:${kind.name}" }
            override fun applyDeclarative(id: String) { calls += "declarative:$id" }
            override fun executeNative(id: String) { calls += "native:$id" }
        }
        val router = PluginCommandDispatcher { null }
        router.dispatch("undo", null, port)
        router.dispatch("none", null, port)
        router.dispatch("pen-menu:open", null, port)
        router.dispatch("advanced-pdf-annotations:open", null, port)
        router.dispatch(requireNotNull(ExportKind.UNIVERSAL.command), null, port)
        router.dispatch("pen-profiles:example", null, port)
        assertEquals(listOf(
            "native:undo", "native:none", "pen-menu",
            "annotations:advanced-pdf-annotations:open",
            "export:UNIVERSAL", "declarative:pen-profiles:example",
        ), calls)
    }
}
