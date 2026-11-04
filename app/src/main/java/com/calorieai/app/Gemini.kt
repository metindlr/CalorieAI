package com.calorieai.app

import android.graphics.Bitmap
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/** Gemini: fotoğraf analizi, yazıdan analiz, AI düzeltme (refine) ve koç yorumu. Yanıt dili `lang` ile ("tr"/"en"). */
class GeminiService(
    private val apiKey: String,
    private val proxyUrl: String = "",
    private val proxyToken: String = "",
) {
    private val client = OkHttpClient.Builder().callTimeout(60, TimeUnit.SECONDS).build()
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }
    private val url = proxyUrl.ifBlank { "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent" }

    suspend fun analyze(bmp: Bitmap, lang: String): Result<Analysis> =
        generate("$PROMPT_IMAGE\n${langRule(lang)}", bmp, true).mapCatching { parse(it) }

    suspend fun analyzeText(desc: String, lang: String): Result<Analysis> =
        generate("$PROMPT_TEXT\nUser text: \"$desc\"\n$SCHEMA\n${langRule(lang)}", null, true).mapCatching { parse(it) }

    /** Kullanıcının düzeltmesini ("pilav yoktu", "yarım porsiyon") önceki analize uygular. */
    suspend fun refine(a: Analysis, correction: String, lang: String): Result<Analysis> {
        val prev = json.encodeToString(Analysis.serializer(), a)
        return generate("$PROMPT_REFINE\nPrevious analysis: $prev\nUser correction: \"$correction\"\n$SCHEMA\n${langRule(lang)}", null, true)
            .mapCatching { parse(it) }
    }

    suspend fun coach(summary: String, lang: String): Result<String> =
        generate("$PROMPT_COACH\n$summary\n${langRule(lang)}", null, false)

    private fun langRule(lang: String) =
        "Response language: ${LANG_NAMES[lang] ?: "English"}. All text fields must be in this language."

    /** AI Şef: "ne yesem" (bağlam), buzdolabı veya menü fotoğrafına göre 3 öneri. */
    suspend fun suggest(mode: String, context: String, bmp: Bitmap?, lang: String): Result<List<Suggestion>> {
        val task = when (mode) { "fridge" -> PROMPT_FRIDGE; "menu" -> PROMPT_MENU; else -> PROMPT_NEXT }
        return generate("$task\n$context\n$SUGGEST_SCHEMA\n${langRule(lang)}", bmp, true)
            .mapCatching { json.decodeFromString<Suggestions>(clean(it)).suggestions }
    }

    private fun clean(t: String) = t.replace("```json", "").replace("```", "").trim()

    private fun parse(t: String): Analysis = json.decodeFromString(clean(t))

    private suspend fun generate(prompt: String, bmp: Bitmap?, asJson: Boolean): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            if (apiKey.isBlank() && proxyUrl.isBlank()) throw UiError(R.string.err_no_key)
            val body = buildJsonObject {
                putJsonArray("contents") {
                    addJsonObject {
                        putJsonArray("parts") {
                            addJsonObject { put("text", prompt) }
                            if (bmp != null) addJsonObject {
                                putJsonObject("inline_data") {
                                    put("mime_type", "image/jpeg")
                                    put("data", bmp.toBase64())
                                }
                            }
                        }
                    }
                }
                putJsonObject("generationConfig") {
                    put("temperature", if (asJson) 0.2 else 0.7)
                    if (asJson) put("responseMimeType", "application/json")
                    putJsonObject("thinkingConfig") { put("thinkingBudget", 0) }
                }
            }.toString()

            val req = Request.Builder().url(url)
                .apply {
                    if (proxyUrl.isNotBlank()) header("X-App-Token", proxyToken)
                    else header("x-goog-api-key", apiKey)
                }
                .post(body.toRequestBody("application/json".toMediaType())).build()

            client.newCall(req).execute().use { r ->
                val txt = r.body?.string().orEmpty()
                if (!r.isSuccessful) throw UiError(
                    when (r.code) {
                        429 -> R.string.err_rate_limit
                        400, 403 -> R.string.err_auth
                        else -> R.string.err_server
                    }, r.code,
                )
                json.parseToJsonElement(txt).jsonObject["candidates"]!!.jsonArray[0]
                    .jsonObject["content"]!!.jsonObject["parts"]!!.jsonArray[0]
                    .jsonObject["text"]!!.jsonPrimitive.content
            }
        }.recoverCatching {
            throw if (it is java.io.IOException) UiError(R.string.err_network) else it
        }
    }

    private fun Bitmap.toBase64(): String {
        val o = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 80, o)
        return Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP)
    }

    private companion object {
        val LANG_NAMES = mapOf(
            "tr" to "Turkish (Türkçe)", "en" to "English", "es" to "Spanish (Español)",
            "pt" to "Brazilian Portuguese (Português do Brasil)", "de" to "German (Deutsch)",
            "fr" to "French (Français)", "ar" to "Arabic (العربية)",
        )

        const val SCHEMA = """Return ONLY JSON in this schema (no other text):
{"isFood":boolean,"dishName":"short dish name","items":[{"name":"item name","grams":int,"calories":int,"protein":number,"carbs":number,"fat":number}],"confidence":0..1,"healthTip":"one-sentence health tip","healthScore":0-100,"scoreReason":"one short sentence why"}
If there is no food, return isFood=false and items=[]. Calories and macros are totals for that item's estimated portion.
healthScore is the overall nutritional quality of the whole meal from 0 (poor) to 100 (excellent), considering fiber, protein balance, added sugar, saturated fat, salt and degree of processing."""

        const val PROMPT_IMAGE = """You are an expert dietitian and food analyst. Analyze the food in the photo.
Estimate portion sizes in grams realistically using references such as plates, forks and glasses. You know Turkish cuisine well.
$SCHEMA"""

        const val PROMPT_TEXT = """You are an expert dietitian. The user describes what they ate in words. If no portion is given, assume a standard portion and estimate realistically. You know Turkish cuisine well."""

        const val PROMPT_REFINE = """You are an expert dietitian. Update the previous food analysis according to the user's correction (remove or add items, change portions, fix the dish). Keep everything the user did not mention unchanged and recompute calories and macros consistently."""

        const val SUGGEST_SCHEMA = """Return ONLY JSON: {"suggestions":[{"name":"dish name","calories":int,"protein":number,"carbs":number,"fat":number,"reason":"one short sentence why it fits","prep":"1-2 sentences: how to make it or how to order it"}]}. Return exactly 3 suggestions. If nothing usable is visible return {"suggestions":[]}."""

        const val PROMPT_NEXT = """You are a dietitian-chef. Suggest 3 realistic meal ideas the user can eat next that fit their remaining calories and macros. Prefer simple, common dishes (including Turkish and Mediterranean home cooking when relevant), vary the ideas and avoid repeating very recent meals."""

        const val PROMPT_FRIDGE = """You are a dietitian-chef. The photo shows a fridge, pantry or ingredients. Identify what you can see, then suggest 3 dishes made mostly from the visible ingredients that fit the user's remaining calories and macros."""

        const val PROMPT_MENU = """You are a dietitian. The photo is a restaurant menu. Read it and pick the 3 best dishes for the user's remaining calories, macros and goal. Estimate calories and macros for a standard serving. In "prep" explain how to order it (for example sauce or dressing on the side, swaps)."""

        const val PROMPT_COACH = """You are a friendly, motivating nutrition coach. Based on the weekly summary below write:
- At most 4 short bullet points (start each with "•"), concrete and actionable
- A final one-sentence motivation
- No medical diagnosis or treatment advice, no exaggerated promises
- At most 90 words in total"""
    }
}
