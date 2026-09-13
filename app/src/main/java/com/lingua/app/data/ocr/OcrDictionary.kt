package com.lingua.app.data.ocr

/**
 * Character table for the recognition head.
 *
 * PaddleOCR reserves class 0 for the CTC blank and appends a space when `use_space_char` is set, so
 * the graph emits `dict.size + 2` classes. The dictionary file itself contains neither.
 */
object OcrDictionary {

  fun characters(dictLines: List<String>, classCount: Int): List<String?> {
    val blankPlusDict = dictLines.size + 1
    val withSpace = blankPlusDict + 1
    require(classCount == blankPlusDict || classCount == withSpace) {
      "model emits $classCount classes but the dictionary has ${dictLines.size} entries"
    }
    val characters = ArrayList<String?>(classCount)
    characters.add(null)
    characters.addAll(dictLines)
    if (classCount == withSpace) characters.add(" ")
    return characters
  }
}
