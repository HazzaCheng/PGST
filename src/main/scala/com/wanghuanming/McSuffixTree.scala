package com.wanghuanming

import org.apache.spark.SparkContext
import org.apache.spark.rdd.RDD

import scala.collection.mutable


class McSuffixTree(terminalSymbol: String = "$", baseHeight: Int = 0) {

  val root = new TreeNode(RangeSubString("", terminalSymbol))

  def insert(str: String, label: String): Unit = {
    // insert all suffixes
    val S = str + terminalSymbol
    for (s <- S.indices.init) {
      // exclude the terminalSymbol
      insertSuffix(RangeSubString(S, s, S.length, label, s))
    }
  }

  def insertSuffix(origin: RangeSubString): Unit = {
    var iter = root
    var remain = origin

    while (remain.nonEmpty) {
      val ch = remain.head

      if (!iter.hasChild(ch)) {
        iter.addChild(remain)
        remain = remain.drop(remain.length)
      } else {
        val child = iter.children(ch)
        val cp = child.seq.commonPrefix(remain)

        if (cp.length < child.seq.length) {
          // split origin child
          iter.updateChild(ch, splitEdgeAt(child, cp.length))
        } else if (cp.length == remain.length) {
          // remain is the leaf
          child.addTerminal(remain)
        }
        remain = remain.drop(cp.length)
      }
      iter = iter.childNode(ch)
    }
  }

  /**
    * r =seq=> origin
    *
    * r =front=> newNode =back=> origin
    */
  private def splitEdgeAt(origin: TreeNode, length: Int): TreeNode = {
    val front = origin.seq.take(length)
    val back = origin.seq.drop(length)
    val newNode = new TreeNode(front)
    origin.seq = back
    newNode.addChild(origin)
    newNode
  }

  def suffixes: Iterable[String] = {
    val leaves = new mutable.ArrayBuffer[String]()

    def dfs(r: TreeNode, height: Int): Unit = {
      r.terminals.foreach(terminal =>
        leaves += Utils.formatNode(terminal.label, height, terminal.index)
      )
      if (r.children.isEmpty && r.seq != null && r.seq.nonEmpty) {
        leaves += Utils.formatNode(r.seq.label, height, r.seq.index)
      } else {
        for ((ch, child) <- r.children) {
          dfs(child, height + 1)
        }
      }
    }

    dfs(root, baseHeight)
    leaves
  }

  /** * 为了方便测试的输出模式 * * @return */
  def suffixesTest: Array[String] = {
    val res = new mutable.ArrayBuffer[String]()
    val buff = new mutable.ArrayBuffer[String]()

    def dfs(r: TreeNode, height: Int): Unit = {
      if (r.children.isEmpty) {
        val str = buff.init.mkString + r.seq
        res += s"${r.seq.label}:$str"
        r.terminals.foreach { terminal =>
          res += s"${terminal.label}:$str"
        }
      } else {
        for ((ch, child) <- r.children) {
          buff += child.seq.mkString
          dfs(child, height + 1)
          buff.reduceToSize(buff.length - 1)
        }
        for (terminal <- r.terminals) {
          res += s"${terminal.label}:${buff.mkString}"
        }
      }
    }

    dfs(root, baseHeight)
    res.toArray.sorted
  }
}

object McSuffixTree {

  def buildByPrefix(str: String, label: String): Array[McSuffixTree] = {
    val alphabet = str.distinct
    val terminal = "$"
    val S = str + terminal
    alphabet.par.map { prefix =>
      val tree = new McSuffixTree(terminal)
      for (i <- S.indices) {
        if (S(i) == prefix) {
          tree.insertSuffix(RangeSubString(S, i, S.length, label, i))
        }
      }
      tree
    }.toArray
  }

  def buildOnSpark(sc: SparkContext, rdd: RDD[RangeSubString], strs: Iterable[RangeSubString], terminal: String): RDD[McSuffixTree] = {
    val alphabet = Utils.getAlphabet(strs)
    val prefixes = verticalPartition(sc, alphabet, rdd, 1000000)

    buildOnSpark(sc, strs, terminal, alphabet, prefixes)
  }

  def verticalPartition(sc: SparkContext, alphabet: String, rdd: RDD[RangeSubString], batchSize: Int): Iterable[String] = {
    sc.setLogLevel("WARN")
    println("=============VertialPartition===============")
    val res = mutable.ArrayBuffer[String]()
    // TODO: read from HDFS directly
    val len2strs = mutable.Map[Int, Map[String, Int]]()

    var pending = mutable.ArrayBuffer[String]() ++ alphabet.map(_.toString)
    var cnt = 0
    while (pending.nonEmpty) {
      cnt += 1
      println(s"Iteration $cnt, res=$res, pending=$pending")
      val counts = pending.map { sprefix =>
        val len = sprefix.length
        val sprefixs = len2strs.getOrElseUpdate(len, {
          rdd.flatMap(_.source.sliding(len).map(_ -> 1))
            .reduceByKey(_ + _)
            .collect()
            .toMap
        }).withDefaultValue(0)
        sprefix -> sprefixs(sprefix)
      }
      val (first, last) = counts.partition { case (x, cnt) => 0 < cnt && cnt <= batchSize }

      res ++= first.map(_._1)
      pending.clear()
      pending ++= last
        .filter(_._2 > 0)
        .map(_._1)
        .flatMap { sprefix => alphabet.map(x => sprefix + x) }
    }
    sc.setLogLevel("INFO")

    println(s"============After Vertical Paritition, ${res.length} parts")
    res
  }

  def buildOnSpark(sc: SparkContext,
                   strs: Iterable[RangeSubString],
                   terminal: String,
                   alphabet: String,
                   prefixes: Iterable[String]): RDD[McSuffixTree] = {
    val strsBV = sc.broadcast(strs)

    sc.parallelize(prefixes.toSeq).map { prefix =>
      val tree = new McSuffixTree(terminal)

      for (str <- strsBV.value) {
        val S = str + terminal
        for (i <- S.indices) {
          if (S.startsWith(prefix, i)) {
            tree.insertSuffix(RangeSubString(S, i, S.length, str.label, i))
          }
        }
      }
      tree
    }
  }

}


