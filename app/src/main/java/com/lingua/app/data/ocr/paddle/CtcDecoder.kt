package com.lingua.app.data.ocr.paddle

import java.nio.FloatBuffer

/**
 * Greedy CTC decoding for the recognition head.
 *
 * The PP-OCRv6 dictionary holds 18708 characters and the graph emits 18710 classes, so class 0 is
 * the CTC blank, classes 1..18708 are the dictionary in file order, and the last class is a space.
 * [characters] is indexed by class id with a null in the blank slot.
 *
 * Rows are read one at a time from the tensor buffer: copying a whole batch out first would cost
 * ~90MB for eight wide lines.
 */
internal object CtcDecoder {

  data class Decoded(val text: String, val confidence: Float)

  fun decode(
    logits: FloatArray,
    batch: Int,
    timeSteps: Int,
    classes: Int,
    characters: List<String?>,
  ): List<Decoded> = decode(FloatBuffer.wrap(logits), batch, timeSteps, classes, characters)

  fun decode(
    logits: FloatBuffer,
    batch: Int,
    timeSteps: Int,
    classes: Int,
    characters: List<String?>,
  ): List<Decoded> {
    require(characters.size == classes) {
      "dictionary has ${characters.size} entries but the model emits $classes classes"
    }
    val row = FloatArray(classes)
    val out = ArrayList<Decoded>(batch)
    for (n in 0 until batch) {
      val base = n * timeSteps * classes
      val builder = StringBuilder()
      var previous = -1
      var probabilitySum = 0f
      var emitted = 0
      for (t in 0 until timeSteps) {
        val offset = base + t * classes
        logits.position(offset)
        logits.get(row, 0, classes)
        var best = 0
        var bestValue = row[0]
        var sum = 0f
        for (c in 0 until classes) {
          val value = row[c]
          if (value > bestValue) {
            bestValue = value
            best = c
          }
          sum += value
        }
        val probability = if (sum in 0.5f..1.5f) bestValue else softmaxValue(row, bestValue)
        if (best != 0 && best != previous) {
          characters[best]?.let { builder.append(it) }
          probabilitySum += probability
          emitted++
        }
        previous = best
      }
      out.add(
        Decoded(
          text = builder.toString(),
          confidence = if (emitted == 0) 0f else probabilitySum / emitted,
        )
      )
    }
    return out
  }

  /** Probability of the winning class after applying softmax to one row of raw logits. */
  private fun softmaxValue(row: FloatArray, max: Float): Float {
    var sum = 0f
    for (value in row) sum += kotlin.math.exp(value - max)
    return 1f / sum
  }
}
