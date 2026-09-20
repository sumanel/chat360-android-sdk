package com.chat360.chatbot.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantModeConfigTest {

    private val roles = listOf(
        AssistantModeOption("Training", mapOf("agent_role" to "training")),
        AssistantModeOption("Customer", mapOf("agent_role" to "customer")),
    )

    @Test
    fun `the default selection is the second button and sends nothing until configured`() {
        val features = FeatureConfig(showAssistantMode = true)
        assertEquals(1, features.initialAssistantModeIndex)
        assertEquals(emptyMap<String, String>(), features.initialAssistantVariables)
    }

    @Test
    fun `the selected option's variables seed the session meta`() {
        val features = FeatureConfig(showAssistantMode = true, assistantModes = roles, defaultAssistantMode = 0)
        assertEquals(mapOf("agent_role" to "training"), features.initialAssistantVariables)
        val customer = features.copy(defaultAssistantMode = 1)
        assertEquals(mapOf("agent_role" to "customer"), customer.initialAssistantVariables)
    }

    @Test
    fun `nothing is sent when the switcher is hidden`() {
        val features = FeatureConfig(showAssistantMode = false, assistantModes = roles)
        assertEquals(emptyMap<String, String>(), features.initialAssistantVariables)
    }

    @Test
    fun `an out-of-range default is coerced instead of crashing`() {
        assertEquals(1, FeatureConfig(assistantModes = roles, defaultAssistantMode = 9).initialAssistantModeIndex)
        assertEquals(0, FeatureConfig(assistantModes = roles, defaultAssistantMode = -3).initialAssistantModeIndex)
        assertEquals(0, FeatureConfig(assistantModes = emptyList()).initialAssistantModeIndex)
    }

    @Test
    fun `the legacy enableTrainingMode flag disables only the first option`() {
        val modes = FeatureConfig(assistantModes = roles, enableTrainingMode = false).effectiveAssistantModes
        assertFalse(modes[0].enabled)
        assertTrue(modes[1].enabled)
        assertTrue(FeatureConfig(assistantModes = roles).effectiveAssistantModes.all { it.enabled })
    }

    @Test
    fun `no more than two buttons are ever shown`() {
        val three = roles + AssistantModeOption("Third", mapOf("agent_role" to "third"))
        val features = FeatureConfig(assistantModes = three)
        assertEquals(listOf("Training", "Customer"), features.effectiveAssistantModes.map { it.label })
        assertEquals(2, FeatureConfig.MAX_ASSISTANT_MODES)
    }

    @Test
    fun `a default pointing at a dropped third button falls back to the last shown one`() {
        val three = roles + AssistantModeOption("Third", mapOf("agent_role" to "third"))
        val features = FeatureConfig(showAssistantMode = true, assistantModes = three, defaultAssistantMode = 2)
        assertEquals(1, features.initialAssistantModeIndex)
        assertEquals(mapOf("agent_role" to "customer"), features.initialAssistantVariables)
    }

    @Test
    fun `a room role maps to the button that sends it`() {
        val features = FeatureConfig(assistantModes = roles)
        assertEquals(0, features.assistantModeIndexForRole("training"))
        assertEquals(1, features.assistantModeIndexForRole("customer"))
    }

    @Test
    fun `an unknown or missing role maps to no button`() {
        val features = FeatureConfig(assistantModes = roles)
        assertEquals(null, features.assistantModeIndexForRole("trainer"))
        assertEquals(null, features.assistantModeIndexForRole(null))
    }

    @Test
    fun `a role only matches on the agent_role variable`() {
        val features = FeatureConfig(assistantModes = listOf(AssistantModeOption("A", mapOf("other" to "training")), roles[1]))
        assertEquals(null, features.assistantModeIndexForRole("training"))
    }

    @Test
    fun `a button dropped by the two-button cap never matches`() {
        val three = roles + AssistantModeOption("Third", mapOf("agent_role" to "third"))
        assertEquals(null, FeatureConfig(assistantModes = three).assistantModeIndexForRole("third"))
    }

    @Test
    fun `a role matches ignoring case and surrounding spaces`() {
        val features = FeatureConfig(assistantModes = roles)
        assertEquals(0, features.assistantModeIndexForRole("Training"))
        assertEquals(1, features.assistantModeIndexForRole("  CUSTOMER "))
        assertEquals(null, features.assistantModeIndexForRole("   "))
    }

    @Test
    fun `a matching role badges as its button with the button's label`() {
        assertEquals(AssistantRoleBadge(0, "Training"), roles.badgeForRole("training"))
        assertEquals(AssistantRoleBadge(1, "Customer"), roles.badgeForRole(" Customer "))
    }

    @Test
    fun `a role no button sends badges as generic with its own text`() {
        assertEquals(AssistantRoleBadge(null, "trainer"), roles.badgeForRole(" trainer "))
    }

    @Test
    fun `a missing or blank role has no badge`() {
        assertEquals(null, roles.badgeForRole(null))
        assertEquals(null, roles.badgeForRole(""))
        assertEquals(null, roles.badgeForRole("   "))
    }

    @Test
    fun `an option disabled in its own config stays disabled`() {
        val modes = FeatureConfig(assistantModes = listOf(roles[0], roles[1].copy(enabled = false))).effectiveAssistantModes
        assertFalse(modes[1].enabled)
    }
}
