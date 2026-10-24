package me.rerere.momoly.data.preset

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetParserTest {
    @Test
    fun parsesSillyTavernEmbeddedRegexScripts() {
        val preset = PresetParser.parse(
            JSONObject(
                """
                {
                  "extensions": {
                    "regex_scripts": [{
                      "id": "nested",
                      "scriptName": "Remove thoughts",
                      "findRegex": "/<think>[\\s\\S]*?<\\/think>/gi",
                      "replaceString": "",
                      "placement": [2],
                      "markdownOnly": true,
                      "promptOnly": false
                    }]
                  }
                }
                """.trimIndent()
            )
        )

        assertEquals(1, preset.regexScripts.size)
        assertEquals("nested", preset.regexScripts.single().id)
        assertEquals("Remove thoughts", preset.regexScripts.single().scriptName)
    }

    @Test
    fun fallsBackToLegacyRootRegexScripts() {
        val preset = PresetParser.parse(
            JSONObject(
                """
                {
                  "regex_scripts": [{
                    "id": "legacy",
                    "scriptName": "Legacy",
                    "findRegex": "/old/g",
                    "replaceString": "new"
                  }]
                }
                """.trimIndent()
            )
        )

        assertEquals("legacy", preset.regexScripts.single().id)
    }

    @Test
    fun standardExtensionFieldWinsWhenBothLayoutsExist() {
        val preset = PresetParser.parse(
            JSONObject(
                """
                {
                  "extensions": {"regex_scripts": [{"id": "standard"}]},
                  "regex_scripts": [{"id": "legacy"}]
                }
                """.trimIndent()
            )
        )

        assertEquals(listOf("standard"), preset.regexScripts.map { it.id })
    }

    @Test
    fun generationParameterSwitchesDefaultOnAndParseExplicitOff() {
        val defaults = PresetParser.parse(JSONObject())
        assertTrue(defaults.sendTemperature)
        assertTrue(defaults.sendTopP)
        assertTrue(defaults.sendTopK)
        assertTrue(defaults.sendFrequencyPenalty)
        assertTrue(defaults.sendPresencePenalty)
        assertTrue(defaults.sendSeed)
        assertTrue(defaults.sendMaxTokens)

        val disabled = PresetParser.parse(
            JSONObject()
                .put("momoly_send_temperature", false)
                .put("momoly_send_top_p", false)
                .put("momoly_send_top_k", false)
                .put("momoly_send_frequency_penalty", false)
                .put("momoly_send_presence_penalty", false)
                .put("momoly_send_seed", false)
                .put("momoly_send_max_tokens", false)
        )
        assertFalse(
            disabled.sendTemperature || disabled.sendTopP || disabled.sendTopK ||
                disabled.sendFrequencyPenalty || disabled.sendPresencePenalty || disabled.sendSeed ||
                disabled.sendMaxTokens
        )
    }
}
