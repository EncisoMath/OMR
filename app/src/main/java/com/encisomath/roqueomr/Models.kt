package com.encisomath.roqueomr

import org.json.JSONObject

data class Exam(
 val id: String, val title: String, val count: Int, val choices: Int, val key: String
) {
 fun toJson(): JSONObject = JSONObject().apply {
  put("id", id); put("title", title); put("count", count)
  put("choices", choices); put("key", key)
 }
 companion object {
  fun fromJson(j: JSONObject) = Exam(
   j.getString("id"), j.getString("title"),
   j.getInt("count"), j.getInt("choices"), j.optString("key", "")
  )
  fun demo() = Exam("demo-40", "DEMO 40 PREGUNTAS", 40, 4,
   (0 until 40).joinToString("") { "ABCD"[it % 4].toString() })
 }
}

data class ScanResult(
 val answers: MutableList<Char>,
 val uncertain: MutableSet<Int>,
 val fillScores: List<List<Double>>,
 val detectedMarkers: Int
) {
 fun correct(exam: Exam): Int = answers.indices.count {
  it < exam.key.length && answers[it] == exam.key[it]
 }
}
