package com.lingua.app.data.ocr.paddle

import com.lingua.app.data.ocr.Quad
import com.lingua.app.data.ocr.RgbImage
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * The polygon maths PaddleOCR's DBNet post-processing relies on, reimplemented in plain Kotlin.
 *
 * Upstream (Apache-2.0) uses OpenCV (`minAreaRect`, `boxPoints`, `warpPerspective`) and pyclipper
 * for the unclip step; none of that is needed for screenshots, so it is done by hand here and the
 * whole thing stays unit-testable on the JVM.
 */
internal object OcrGeometry {

  /** Shoelace formula; positive when the corners run counter-clockwise in math orientation. */
  fun signedArea(quad: Quad): Float {
    var sum = 0f
    for (i in 0 until 4) {
      val j = (i + 1) % 4
      sum += quad.x(i) * quad.y(j) - quad.x(j) * quad.y(i)
    }
    return sum / 2f
  }

  fun area(quad: Quad): Float = abs(signedArea(quad))

  fun perimeter(quad: Quad): Float =
    (0 until 4).sumOf { hypot((quad.x((it + 1) % 4) - quad.x(it)).toDouble(), (quad.y((it + 1) % 4) - quad.y(it)).toDouble()) }.toFloat()

  /** Re-orders corners to top-left, top-right, bottom-right, bottom-left. */
  fun orderQuad(quad: Quad): Quad {
    var start = 0
    for (i in 1 until 4) {
      if (quad.y(i) < quad.y(start) || (quad.y(i) == quad.y(start) && quad.x(i) < quad.x(start))) {
        start = i
      }
    }
    // The stored order is cyclic, so the two neighbours of `start` are its +1/-1 steps and the
    // corner two steps away is the diagonal. The neighbour further right is the top-right corner.
    val forward = (start + 1) % 4
    val backward = (start + 3) % 4
    val next = if (quad.x(backward) > quad.x(forward)) backward else forward
    val diagonal = (start + 2) % 4
    val last = (0 until 4).first { it != start && it != next && it != diagonal }
    val order = intArrayOf(start, next, diagonal, last)
    return Quad(FloatArray(4) { quad.x(order[it]) }, FloatArray(4) { quad.y(order[it]) })
  }

  private data class Pt(val x: Float, val y: Float)

  /**
   * Smallest-area enclosing rectangle (rotating calipers over the convex hull), the equivalent of
   * `cv2.minAreaRect`. Returns corners in cyclic order, or null when there are too few points.
   */
  fun minAreaRect(xs: FloatArray, ys: FloatArray): Quad? {
    val hull = convexHull(xs, ys) ?: return null
    if (hull.size < 3) return null

    var bestArea = Float.MAX_VALUE
    var bestEx = 1f
    var bestEy = 0f
    var bestNx = 0f
    var bestNy = 1f
    var bestMinE = 0f
    var bestMaxE = 0f
    var bestMinN = 0f
    var bestMaxN = 0f

    for (i in hull.indices) {
      val a = hull[i]
      val b = hull[(i + 1) % hull.size]
      var ex = b.x - a.x
      var ey = b.y - a.y
      val length = hypot(ex, ey)
      if (length < 1e-6f) continue
      ex /= length
      ey /= length
      val nx = -ey
      val ny = ex

      var minE = Float.MAX_VALUE
      var maxE = -Float.MAX_VALUE
      var minN = Float.MAX_VALUE
      var maxN = -Float.MAX_VALUE
      for (p in hull) {
        val pe = p.x * ex + p.y * ey
        val pn = p.x * nx + p.y * ny
        if (pe < minE) minE = pe
        if (pe > maxE) maxE = pe
        if (pn < minN) minN = pn
        if (pn > maxN) maxN = pn
      }
      val area = (maxE - minE) * (maxN - minN)
      if (area < bestArea) {
        bestArea = area
        bestEx = ex
        bestEy = ey
        bestNx = nx
        bestNy = ny
        bestMinE = minE
        bestMaxE = maxE
        bestMinN = minN
        bestMaxN = maxN
      }
    }
    if (bestArea == Float.MAX_VALUE) return null

    fun corner(pe: Float, pn: Float): Pt =
      Pt(pe * bestEx + pn * bestNx, pe * bestEy + pn * bestNy)

    val corners =
      listOf(
        corner(bestMinE, bestMinN),
        corner(bestMaxE, bestMinN),
        corner(bestMaxE, bestMaxN),
        corner(bestMinE, bestMaxN),
      )
    return Quad(FloatArray(4) { corners[it].x }, FloatArray(4) { corners[it].y })
  }

  /** Short side of a box, used to drop noise specks and hairline regions. */
  fun shortSide(quad: Quad): Float = min(quad.edgeWidth, quad.edgeHeight)

  private fun convexHull(sourceXs: FloatArray, sourceYs: FloatArray): List<Pt>? {
    if (sourceXs.size < 3) return null
    val points = ArrayList<Pt>(sourceXs.size)
    for (i in sourceXs.indices) points.add(Pt(sourceXs[i], sourceYs[i]))
    points.sortWith(compareBy({ it.x }, { it.y }))
    // Drop duplicates, which break the cross-product test below.
    val unique = ArrayList<Pt>(points.size)
    for (p in points) {
      if (unique.isEmpty() || unique.last().x != p.x || unique.last().y != p.y) unique.add(p)
    }
    if (unique.size < 3) return null

    fun cross(o: Pt, a: Pt, b: Pt): Float =
      (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)

    val lower = ArrayList<Pt>()
    for (p in unique) {
      while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0f) {
        lower.removeAt(lower.size - 1)
      }
      lower.add(p)
    }
    val upper = ArrayList<Pt>()
    for (i in unique.indices.reversed()) {
      val p = unique[i]
      while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0f) {
        upper.removeAt(upper.size - 1)
      }
      upper.add(p)
    }
    lower.removeAt(lower.size - 1)
    upper.removeAt(upper.size - 1)
    lower.addAll(upper)
    return if (lower.size < 3) null else lower
  }

  /**
   * Pushes a box outwards by `area * ratio / perimeter` points, which is what pyclipper's
   * `PyclipperOffset` does upstream with `delta = area * unclip_ratio / perimeter`.
   *
   * Corners are rebuilt as the intersection of the two shifted edges (miter join), falling back to
   * the averaged normal when neighbouring edges are nearly parallel.
   */
  fun unclip(quad: Quad, ratio: Float): Quad {
    val area = area(quad)
    val perimeter = perimeter(quad)
    if (area <= 0f || perimeter <= 0f) return quad
    val delta = area * ratio / perimeter
    if (delta <= 0f) return quad

    val counterClockwise = signedArea(quad) > 0f
    val nx = FloatArray(4)
    val ny = FloatArray(4)
    val ex = FloatArray(4)
    val ey = FloatArray(4)
    for (i in 0 until 4) {
      val j = (i + 1) % 4
      var dx = quad.x(j) - quad.x(i)
      var dy = quad.y(j) - quad.y(i)
      val length = hypot(dx, dy)
      if (length < 1e-6f) continue
      dx /= length
      dy /= length
      ex[i] = dx
      ey[i] = dy
      nx[i] = if (counterClockwise) dy else -dy
      ny[i] = if (counterClockwise) -dx else dx
    }

    val outX = FloatArray(4)
    val outY = FloatArray(4)
    for (i in 0 until 4) {
      val previous = (i + 3) % 4
      // Edge `previous` shifted outwards.
      val ax = quad.x(previous) + nx[previous] * delta
      val ay = quad.y(previous) + ny[previous] * delta
      // Edge `i` shifted outwards.
      val bx = quad.x(i) + nx[i] * delta
      val by = quad.y(i) + ny[i] * delta
      val denominator = ex[previous] * ey[i] - ey[previous] * ex[i]
      if (abs(denominator) < 1e-6f) {
        val mx = nx[previous] + nx[i]
        val my = ny[previous] + ny[i]
        val length = hypot(mx, my)
        if (length < 1e-6f) {
          outX[i] = quad.x(i)
          outY[i] = quad.y(i)
        } else {
          outX[i] = quad.x(i) + mx / length * delta
          outY[i] = quad.y(i) + my / length * delta
        }
      } else {
        val t = ((bx - ax) * ey[i] - (by - ay) * ex[i]) / denominator
        outX[i] = ax + ex[previous] * t
        outY[i] = ay + ey[previous] * t
      }
    }
    return Quad(outX, outY)
  }

  fun contains(quad: Quad, x: Float, y: Float): Boolean {
    var inside = false
    var j = 3
    for (i in 0 until 4) {
      val yi = quad.y(i)
      val yj = quad.y(j)
      if ((yi > y) != (yj > y)) {
        val xi = quad.x(i)
        val crossing = (quad.x(j) - xi) * (y - yi) / (yj - yi) + xi
        if (x < crossing) inside = !inside
      }
      j = i
    }
    return inside
  }

  /** Mean probability inside the box, used to reject low-confidence detections. */
  fun boxScore(prob: FloatArray, width: Int, height: Int, quad: Quad): Float {
    val x0 = quad.minX.toInt().coerceIn(0, width - 1)
    val x1 = ceil(quad.maxX.toDouble()).toInt().coerceIn(0, width - 1)
    val y0 = quad.minY.toInt().coerceIn(0, height - 1)
    val y1 = ceil(quad.maxY.toDouble()).toInt().coerceIn(0, height - 1)
    if (x1 < x0 || y1 < y0) return 0f
    var sum = 0f
    var count = 0
    for (y in y0..y1) {
      val sy = y + 0.5f
      for (x in x0..x1) {
        if (!contains(quad, x + 0.5f, sy)) continue
        sum += prob[y * width + x]
        count++
      }
    }
    return if (count == 0) 0f else sum / count
  }

  /**
   * Samples a quadrilateral out of [image] into [outWidth] x [outHeight] ARGB pixels.
   *
   * Builds the homography straight from the destination rectangle to the source quad, so the
   * per-pixel mapping needs no matrix inversion.
   */
  fun cropQuad(image: RgbImage, quad: Quad, outWidth: Int, outHeight: Int): IntArray {
    val h = homography(
      fromX = floatArrayOf(0f, outWidth.toFloat(), outWidth.toFloat(), 0f),
      fromY = floatArrayOf(0f, 0f, outHeight.toFloat(), outHeight.toFloat()),
      toX = quad.xs,
      toY = quad.ys,
    )
    val out = IntArray(outWidth * outHeight)
    for (y in 0 until outHeight) {
      for (x in 0 until outWidth) {
        val u = x + 0.5f
        val v = y + 0.5f
        val denominator = h[6] * u + h[7] * v + h[8]
        if (abs(denominator) < 1e-9f) {
          out[y * outWidth + x] = ARGB_OPAQUE_BLACK
          continue
        }
        val sx = (h[0] * u + h[1] * v + h[2]) / denominator
        val sy = (h[3] * u + h[4] * v + h[5]) / denominator
        out[y * outWidth + x] = sampleBilinear(image, sx, sy)
      }
    }
    return out
  }

  private const val ARGB_OPAQUE_BLACK = 0xFF000000.toInt()

  internal fun sampleBilinear(image: RgbImage, x: Float, y: Float): Int {
    val clampedX = x.coerceIn(0f, (image.width - 1).toFloat())
    val clampedY = y.coerceIn(0f, (image.height - 1).toFloat())
    val x0 = clampedX.toInt()
    val y0 = clampedY.toInt()
    val x1 = min(x0 + 1, image.width - 1)
    val y1 = min(y0 + 1, image.height - 1)
    val fx = clampedX - x0
    val fy = clampedY - y0

    val p00 = image.argb[y0 * image.width + x0]
    val p10 = image.argb[y0 * image.width + x1]
    val p01 = image.argb[y1 * image.width + x0]
    val p11 = image.argb[y1 * image.width + x1]

    fun channel(shift: Int): Int {
      val a = (p00 shr shift and 0xFF) * (1 - fx) + (p10 shr shift and 0xFF) * fx
      val b = (p01 shr shift and 0xFF) * (1 - fx) + (p11 shr shift and 0xFF) * fx
      return (a * (1 - fy) + b * fy).toInt().coerceIn(0, 255)
    }
    val r = channel(16)
    val g = channel(8)
    val b = channel(0)
    return ARGB_OPAQUE_BLACK or (r shl 16) or (g shl 8) or b
  }

  /**
   * Solves the 8 unknowns of the homography taking the `from` points onto the `to` points, returned
   * row-major as a 3x3 with the bottom-right element fixed to 1.
   */
  internal fun homography(
    fromX: FloatArray,
    fromY: FloatArray,
    toX: FloatArray,
    toY: FloatArray,
  ): FloatArray {
    val m = Array(8) { DoubleArray(9) }
    for (i in 0 until 4) {
      val x = fromX[i].toDouble()
      val y = fromY[i].toDouble()
      val u = toX[i].toDouble()
      val v = toY[i].toDouble()
      m[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
      m[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
    }
    // Gauss-Jordan with partial pivoting.
    for (column in 0 until 8) {
      var pivot = column
      for (row in column + 1 until 8) {
        if (abs(m[row][column]) > abs(m[pivot][column])) pivot = row
      }
      val swap = m[column]
      m[column] = m[pivot]
      m[pivot] = swap
      val diagonal = m[column][column]
      if (abs(diagonal) < 1e-12) return floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)
      for (k in column..8) m[column][k] /= diagonal
      for (row in 0 until 8) {
        if (row == column) continue
        val factor = m[row][column]
        if (factor == 0.0) continue
        for (k in column..8) m[row][k] -= factor * m[column][k]
      }
    }
    return FloatArray(9) { if (it < 8) m[it][8].toFloat() else 1f }
  }

  /** Clamps a crop width so a full-width line keeps its aspect ratio without blowing up the batch. */
  fun clampCropWidth(width: Int, maxWidth: Int): Int = max(MIN_CROP_WIDTH, min(width, maxWidth))

  const val MIN_CROP_WIDTH = 16
}
