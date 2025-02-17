// SPDX-License-Identifier: Apache-2.0

/** Mux circuit generators.
  */

package chisel3.util

import chisel3._
import chisel3.experimental.SourceInfo

/** Builds a Mux tree out of the input signal vector using a one hot encoded
  * select signal. Returns the output of the Mux tree.
  *
  * @example {{{
  * val hotValue = chisel3.util.Mux1H(Seq(
  *  io.selector(0) -> 2.U,
  *  io.selector(1) -> 4.U,
  *  io.selector(2) -> 8.U,
  *  io.selector(4) -> 11.U,
  * ))
  * }}}
  *
  * @note results unspecified unless exactly one select signal is high
  */
object Mux1H {
  def apply[T <: Data](sel: Seq[Bool], in: Seq[T]): T = {
    require(sel.size == in.size, s"Mux1H: input Seqs must have the same length, got sel ${sel.size} and in ${in.size}")
    apply(sel.zip(in))
  }
  def apply[T <: Data](in:  Iterable[(Bool, T)]): T = SeqUtils.oneHotMux(in)
  def apply[T <: Data](sel: UInt, in: Seq[T]): T =
    apply((0 until in.size).map(sel(_)), in)
  def apply(sel: UInt, in: UInt): Bool = (sel & in).orR
}

/** Builds a Mux tree under the assumption that multiple select signals
  * can be enabled. Priority is given to the first select signal.
  *
  * @example {{{
  * val hotValue = chisel3.util.PriorityMux(Seq(
  *  io.selector(0) -> 2.U,
  *  io.selector(1) -> 4.U,
  *  io.selector(2) -> 8.U,
  *  io.selector(4) -> 11.U,
  * ))
  * }}}
  * Returns the output of the Mux tree.
  */
object PriorityMux {
  def apply[T <: Data](in: Seq[(Bool, T)]): T = SeqUtils.priorityMux(in)
  def apply[T <: Data](sel: Seq[Bool], in: Seq[T]): T = {
    require(
      sel.size == in.size,
      s"PriorityMux: input Seqs must have the same length, got sel ${sel.size} and in ${in.size}"
    )
    apply(sel.zip(in))
  }
  def apply[T <: Data](sel: Bits, in: Seq[T]): T = apply((0 until in.size).map(sel(_)), in)
}

private[chisel3] trait MuxLookupImpl {

  protected def _applyEnumImpl[S <: EnumType, T <: Data](
    key:     S,
    default: T,
    mapping: Seq[(S, T)]
  )(
    implicit sourceinfo: SourceInfo
  ): T =
    _applyImpl[UInt, T](key.asUInt, default, mapping.map { case (s, t) => (s.asUInt, t) })

  protected def _applyImpl[S <: UInt, T <: Data](
    key:     S,
    default: T,
    mapping: Seq[(S, T)]
  )(
    implicit sourceinfo: SourceInfo
  ): T = {
    /* If the mapping is defined for all possible values of the key, then don't use the default value */
    val (defaultx, mappingx) = key.widthOption match {
      case Some(width) =>
        val keySetSize = BigInt(1) << width
        val keyMask = keySetSize - 1
        val distinctLitKeys = mapping.flatMap(_._1.litOption).map(_ & keyMask).distinct
        if (distinctLitKeys.size == keySetSize) {
          (mapping.head._2, mapping.tail)
        } else {
          (default, mapping)
        }
      case None => (default, mapping)
    }

    mappingx.foldLeft(defaultx) { case (d, (k, v)) => Mux(k === key, v, d) }
  }
}

/** Given an association of values to enable signals, returns the first value with an associated
  * high enable signal.
  *
  * @example {{{
  * MuxCase(default, Array(c1 -> a, c2 -> b))
  * }}}
  */
object MuxCase {

  /** @param default the default value if none are enabled
    * @param mapping a set of data values with associated enables
    * @return the first value in mapping that is enabled
    */
  def apply[T <: Data](default: T, mapping: Seq[(Bool, T)]): T = {
    var res = default
    for ((t, v) <- mapping.reverse) {
      res = Mux(t, v, res)
    }
    res
  }
}
