package com.ridervoice.cockpit.domain

import com.ridervoice.cockpit.model.CopilotMessage
import com.ridervoice.cockpit.model.HazardType
import com.ridervoice.cockpit.model.MessageSender
import com.ridervoice.cockpit.model.PlaceCategory
import com.ridervoice.cockpit.model.RiderRole
import com.ridervoice.cockpit.model.RiderVoiceUiState

object CopilotToolRegistry {

    sealed class ToolExecutionResult(open val replyText: String = "") {
        data class SearchPlaces(
            val query: String,
            val category: PlaceCategory,
            override val replyText: String
        ) : ToolExecutionResult(replyText)

        data class SetGoal(
            val destinationName: String,
            override val replyText: String
        ) : ToolExecutionResult(replyText)

        data class AddRider(
            val callsign: String,
            val role: RiderRole,
            override val replyText: String
        ) : ToolExecutionResult(replyText)

        data class RemoveRider(
            val callsign: String,
            override val replyText: String
        ) : ToolExecutionResult(replyText)

        object DeactivateGroup : ToolExecutionResult("Deactivated squad group session.")

        data class BroadcastHazard(
            val type: HazardType,
            override val replyText: String
        ) : ToolExecutionResult(replyText)

        data class LaunchExternalNav(
            val destinationName: String,
            val lat: Double,
            val lon: Double,
            override val replyText: String
        ) : ToolExecutionResult(replyText)

        data class ControlMedia(
            val action: MediaAction,
            override val replyText: String
        ) : ToolExecutionResult(replyText)

        data class AdjustVolume(
            val increase: Boolean,
            val isMax: Boolean,
            override val replyText: String
        ) : ToolExecutionResult(replyText)

        data class ToggleTorch(
            val enable: Boolean,
            override val replyText: String
        ) : ToolExecutionResult(replyText)

        data class MakeCall(
            val phoneNumber: String,
            val contactName: String,
            override val replyText: String
        ) : ToolExecutionResult(replyText)

        data class AnswerCall(
            override val replyText: String
        ) : ToolExecutionResult(replyText)

        data class DeclineCall(
            override val replyText: String
        ) : ToolExecutionResult(replyText)

        data class SendSMS(
            val phoneNumber: String,
            val messageText: String,
            override val replyText: String
        ) : ToolExecutionResult(replyText)

        data class InfoResponse(
            override val replyText: String,
            val toolBadge: String? = null
        ) : ToolExecutionResult(replyText)
    }

    /**
     * Parses user prompt and evaluates the appropriate GenAI function call / tool.
     */
    fun parseAndExecutePrompt(
        prompt: String,
        currentState: RiderVoiceUiState
    ): ToolExecutionResult {
        val lower = prompt.lowercase().trim()

        return when {
            // Incoming Call Voice Actions (Answer / Decline)
            lower == "answer" || lower.contains("answer call") || lower.contains("pick up") || lower.contains("accept call") -> {
                ToolExecutionResult.AnswerCall("Answering incoming call hands-free.")
            }
            lower == "decline" || lower.contains("decline call") || lower.contains("reject call") || lower.contains("cut call") -> {
                ToolExecutionResult.DeclineCall("Declining incoming call.")
            }

            // Contact Search & Dialing
            lower.contains("call ") || lower.contains("dial ") -> {
                val contactName = when {
                    lower.contains("ujjawal") -> "Ujjawal Ranjan"
                    lower.contains("rohan") -> "Rohan"
                    lower.contains("captain") || lower.contains("aarav") -> "Aarav"
                    else -> lower.substringAfter("call ").substringAfter("dial ").trim().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
                ToolExecutionResult.MakeCall(
                    phoneNumber = "",
                    contactName = contactName,
                    replyText = "Searching contacts for '$contactName' and placing call..."
                )
            }

            // Flashlight / Torch Control
            lower.contains("flashlight") || lower.contains("torch") || lower.contains("light on") || lower.contains("turn on light") -> {
                ToolExecutionResult.ToggleTorch(
                    enable = true,
                    replyText = "Turning ON the LED Flashlight / Torch for night inspection."
                )
            }
            lower.contains("light off") || lower.contains("torch off") || lower.contains("turn off light") -> {
                ToolExecutionResult.ToggleTorch(
                    enable = false,
                    replyText = "Turning OFF the LED Torch."
                )
            }

            // Volume Controls
            lower.contains("max volume") || lower.contains("full volume") || lower.contains("loudest") -> {
                ToolExecutionResult.AdjustVolume(
                    increase = true,
                    isMax = true,
                    replyText = "Setting system media volume to maximum."
                )
            }
            lower.contains("volume up") || lower.contains("increase volume") || lower.contains("louder") -> {
                ToolExecutionResult.AdjustVolume(
                    increase = true,
                    isMax = false,
                    replyText = "Increasing media volume."
                )
            }
            lower.contains("volume down") || lower.contains("decrease volume") || lower.contains("quieter") -> {
                ToolExecutionResult.AdjustVolume(
                    increase = false,
                    isMax = false,
                    replyText = "Decreasing media volume."
                )
            }

            // Media & Music Playback (Spotify / YouTube Music / System Player)
            lower.contains("play music") || lower.contains("resume spotify") || lower.contains("play track") -> {
                ToolExecutionResult.ControlMedia(
                    action = MediaAction.PLAY_PAUSE,
                    replyText = "Resuming music playback."
                )
            }
            lower.contains("pause music") || lower.contains("pause spotify") || lower.contains("stop music") -> {
                ToolExecutionResult.ControlMedia(
                    action = MediaAction.PLAY_PAUSE,
                    replyText = "Pausing music playback."
                )
            }
            lower.contains("next song") || lower.contains("next track") || lower.contains("skip song") || lower.contains("skip track") -> {
                ToolExecutionResult.ControlMedia(
                    action = MediaAction.NEXT,
                    replyText = "Skipping to next track."
                )
            }
            lower.contains("previous song") || lower.contains("previous track") -> {
                ToolExecutionResult.ControlMedia(
                    action = MediaAction.PREVIOUS,
                    replyText = "Going back to previous track."
                )
            }

            // Phone Calls & SMS
            lower.contains("call emergency") || lower.contains("dial 112") || lower.contains("call 911") || lower.contains("sos call") -> {
                ToolExecutionResult.MakeCall(
                    phoneNumber = "112",
                    contactName = "Emergency Highway Patrol",
                    replyText = "Opening phone dialer for Emergency Services (112)."
                )
            }
            lower.contains("send message") || lower.contains("text squad") || lower.contains("sms") -> {
                ToolExecutionResult.SendSMS(
                    phoneNumber = "+919876543210",
                    messageText = "RiderVoice Alert: Squad pit stop requested at current GPS position.",
                    replyText = "Opening SMS messaging to squad pack."
                )
            }

            // External Navigation (Google Maps)
            lower.contains("google maps") || lower.contains("external map") || lower.contains("waze") -> {
                val targetName = when {
                    lower.contains("amber fort") -> "Amber Fort Viewpoint"
                    lower.contains("nahargarh") -> "Nahargarh Fort Biker Cafe"
                    else -> "Amber Fort Viewpoint"
                }
                val lat = if (lower.contains("nahargarh")) 26.9373 else 26.9855
                val lon = if (lower.contains("nahargarh")) 75.8155 else 75.8513
                ToolExecutionResult.LaunchExternalNav(
                    destinationName = targetName,
                    lat = lat,
                    lon = lon,
                    replyText = "Launching Google Maps turn-by-turn navigation for '$targetName'."
                )
            }

            // Internal Squad Destination / Goal
            lower.contains("set goal") || lower.contains("navigate to") || lower.contains("lock destination") || lower.contains("go to") -> {
                val targetName = when {
                    lower.contains("amber fort") -> "Amber Fort Viewpoint & Riders Point"
                    lower.contains("nahargarh") -> "Nahargarh Sunset Biker Cafe"
                    lower.contains("jal mahal") -> "Jal Mahal Lakefront Pull-Off"
                    lower.contains("fuel") -> "HP Highway Fuel & Nitrogen Hub"
                    else -> "Amber Fort Viewpoint & Riders Point"
                }
                ToolExecutionResult.SetGoal(
                    destinationName = targetName,
                    replyText = "Locked '$targetName' as the shared squad goal. Polyline updated and broadcasted to all co-riders."
                )
            }

            // Place & POI Search
            lower.contains("fuel") || lower.contains("petrol") || lower.contains("gas") -> {
                ToolExecutionResult.SearchPlaces(
                    query = "Fuel",
                    category = PlaceCategory.FUEL,
                    replyText = "Searching for nearby high-flow petrol stations and nitrogen air stations along your highway route."
                )
            }
            lower.contains("cafe") || lower.contains("coffee") || lower.contains("food") -> {
                ToolExecutionResult.SearchPlaces(
                    query = "Cafe",
                    category = PlaceCategory.BIKER_CAFE,
                    replyText = "Locating biker-friendly cafes and highway pit stops nearby."
                )
            }
            lower.contains("repair") || lower.contains("mechanic") || lower.contains("puncture") || lower.contains("tyre") -> {
                ToolExecutionResult.SearchPlaces(
                    query = "Mechanic",
                    category = PlaceCategory.MECHANIC,
                    replyText = "Scanning for ADV repair shops, chain lube pit stops, and puncture mechanics."
                )
            }
            lower.contains("viewpoint") || lower.contains("scenic") || lower.contains("fort") || lower.contains("lake") -> {
                ToolExecutionResult.SearchPlaces(
                    query = "Viewpoint",
                    category = PlaceCategory.VIEWPOINT,
                    replyText = "Finding scenic canyon viewpoints and rider pull-offs along the highway."
                )
            }

            // Squad Management: Add Rider
            lower.contains("add rider") || lower.contains("invite rider") || lower.contains("add co-rider") -> {
                val callsign = if (lower.contains("kabir")) "Kabir" else "Dev"
                ToolExecutionResult.AddRider(
                    callsign = callsign,
                    role = RiderRole.PACK_RIDER,
                    replyText = "Added '$callsign' [PACK] to the squad mesh. Syncing GPS telemetry and assigning intercom slot."
                )
            }

            // Squad Management: Remove Rider
            lower.contains("remove") || lower.contains("kick") -> {
                val targetCallsign = currentState.coRiders.firstOrNull()?.callsign ?: "Co-Rider"
                ToolExecutionResult.RemoveRider(
                    callsign = targetCallsign,
                    replyText = "Removed '$targetCallsign' from squad group and revoked intercom channel."
                )
            }

            // Squad Management: Deactivate Group
            lower.contains("deactivate group") || lower.contains("end group") || lower.contains("dissolve squad") -> {
                ToolExecutionResult.DeactivateGroup
            }

            // Road Hazard Broadcast
            lower.contains("pothole") -> {
                ToolExecutionResult.BroadcastHazard(
                    type = HazardType.POTHOLE,
                    replyText = "Dispatched DEEP POTHOLE warning pin at your GPS position. Audio alert broadcasted to pack."
                )
            }
            lower.contains("radar") || lower.contains("speed trap") || lower.contains("police") -> {
                ToolExecutionResult.BroadcastHazard(
                    type = HazardType.SPEED_TRAP,
                    replyText = "Broadcasted RADAR / SPEED TRAP hazard warning to all squad members."
                )
            }
            lower.contains("oil") || lower.contains("gravel") || lower.contains("slippery") -> {
                ToolExecutionResult.BroadcastHazard(
                    type = HazardType.OIL_GRAVEL,
                    replyText = "Caution! Broadcasted SLIPPERY GRAVEL / OIL alert to squad."
                )
            }

            // Helmet & Telemetry Query
            lower.contains("battery") || lower.contains("headset") || lower.contains("cardo") || lower.contains("sena") -> {
                val localBattery = currentState.headset.batteryPct
                val minRider = currentState.coRiders.minByOrNull { it.helmetBatteryPct }
                val lowestText = if (minRider != null) {
                    " Lowest co-rider helmet battery is ${minRider.callsign} at ${minRider.helmetBatteryPct}%."
                } else ""
                ToolExecutionResult.InfoResponse(
                    replyText = "Your ${currentState.headset.deviceName} battery is at $localBattery% (${currentState.headset.codec.label}).$lowestText",
                    toolBadge = "HELMET_TELEMETRY"
                )
            }

            // Squad Formation Status
            lower.contains("where is") || lower.contains("status") || lower.contains("squad formation") || lower.contains("pack") -> {
                val squadCount = currentState.coRiders.size + 1
                val leadName = currentState.coRiders.firstOrNull { it.role == RiderRole.ROAD_CAPTAIN }?.callsign ?: "You"
                val sweepName = currentState.coRiders.firstOrNull { it.role == RiderRole.TAIL_GUNNER }?.callsign ?: "Rohan"
                ToolExecutionResult.InfoResponse(
                    replyText = "Squad has $squadCount riders active in ${currentState.squadGroup.transportMode.label}. Lead is $leadName, Sweep is $sweepName. Pack spread is 380m.",
                    toolBadge = "FORMATION_STATUS"
                )
            }

            // Generic AI Copilot Fallback Response
            else -> {
                ToolExecutionResult.InfoResponse(
                    replyText = "Copilot active. You can say 'Call Ujjawal Ranjan', 'Answer', 'Decline', 'Turn on flashlight', 'Volume up', 'Play music', or 'Navigate in Google Maps'.",
                    toolBadge = "COPILOT_ASSIST"
                )
            }
        }
    }
}
