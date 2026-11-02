package com.ridervoice.cockpit

import com.ridervoice.cockpit.domain.CopilotToolRegistry
import com.ridervoice.cockpit.domain.GeoMathAndFreeMapEngine
import com.ridervoice.cockpit.model.GeoPointData
import com.ridervoice.cockpit.model.PlaceCategory
import com.ridervoice.cockpit.model.RiderVoiceUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiderVoiceUnitTest {

    @Test
    fun testHaversineDistanceCalculation() {
        val p1 = GeoPointData(26.9124, 75.7873)
        val p2 = GeoPointData(26.9855, 75.8513)
        val distance = GeoMathAndFreeMapEngine.calculateDistanceMeters(p1, p2)
        assertTrue("Distance should be greater than 5000m", distance > 5000)
    }

    @Test
    fun testBearingCalculation() {
        val p1 = GeoPointData(26.9124, 75.7873)
        val p2 = GeoPointData(26.9855, 75.8513)
        val bearing = GeoMathAndFreeMapEngine.calculateBearingDeg(p1, p2)
        assertTrue("Bearing should be between 0 and 360 degrees", bearing in 0f..360f)
    }

    @Test
    fun testCopilotFuelSearchParsing() {
        val state = RiderVoiceUiState()
        val result = CopilotToolRegistry.parseAndExecutePrompt("Find nearest fuel station", state)
        assertTrue("Should result in SearchPlaces tool execution", result is CopilotToolRegistry.ToolExecutionResult.SearchPlaces)
        val search = result as CopilotToolRegistry.ToolExecutionResult.SearchPlaces
        assertEquals(PlaceCategory.FUEL, search.category)
    }

    @Test
    fun testCopilotHazardBroadcastParsing() {
        val state = RiderVoiceUiState()
        val result = CopilotToolRegistry.parseAndExecutePrompt("Broadcast pothole hazard", state)
        assertTrue("Should result in BroadcastHazard tool execution", result is CopilotToolRegistry.ToolExecutionResult.BroadcastHazard)
    }

    @Test
    fun testCopilotSetGoalParsing() {
        val state = RiderVoiceUiState()
        val result = CopilotToolRegistry.parseAndExecutePrompt("Navigate to Amber Fort", state)
        assertTrue("Should result in SetGoal tool execution", result is CopilotToolRegistry.ToolExecutionResult.SetGoal)
    }

    @Test
    fun testCopilotCallContactParsing() {
        val state = RiderVoiceUiState()
        val result = CopilotToolRegistry.parseAndExecutePrompt("call ujjawal", state)
        assertTrue("Should result in MakeCall tool execution", result is CopilotToolRegistry.ToolExecutionResult.MakeCall)
        val callResult = result as CopilotToolRegistry.ToolExecutionResult.MakeCall
        assertEquals("Ujjawal Ranjan", callResult.contactName)
    }

    @Test
    fun testCopilotAnswerAndDeclineCallParsing() {
        val state = RiderVoiceUiState()
        val answerResult = CopilotToolRegistry.parseAndExecutePrompt("answer", state)
        assertTrue("Should result in AnswerCall tool execution", answerResult is CopilotToolRegistry.ToolExecutionResult.AnswerCall)

        val declineResult = CopilotToolRegistry.parseAndExecutePrompt("decline", state)
        assertTrue("Should result in DeclineCall tool execution", declineResult is CopilotToolRegistry.ToolExecutionResult.DeclineCall)
    }
}
