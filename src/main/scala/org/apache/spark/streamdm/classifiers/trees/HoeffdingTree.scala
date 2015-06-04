/*
 * Copyright (C) 2015 Holmes Team at HUAWEI Noah's Ark Lab.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.spark.streamdm.classifiers.trees

import scala.collection.mutable.ArrayBuffer
import scala.math.{ log => math_log, sqrt }

import org.apache.spark.Logging

import com.github.javacliparser._

import org.apache.spark.streaming.dstream._
import org.apache.spark.streamdm.util.Util.{ argmax, arraytoString }
import org.apache.spark.streamdm.core._
import org.apache.spark.streamdm.classifiers._

class HoeffdingTree extends Classifier {

  type T = HoeffdingTreeModel

  val numClassesOption: IntOption = new IntOption("numClasses", 'h',
    "Number of Classes", 2, 2, Integer.MAX_VALUE)

  val numFeaturesOption: IntOption = new IntOption("numFeatures", 'f',
    "Number of Features", 3, 1, Integer.MAX_VALUE)

  //  val featureArray = new FeatureTypeArray(Array[FeatureType](new NumericFeatureType(), new NumericFeatureType(), new NumericFeatureType()))
  val featureArray = new FeatureTypeArray(Array[FeatureType](new NominalFeatureType(10), new NominalFeatureType(10), new NominalFeatureType(10)))

  val numericObserverTypeOption: IntOption = new IntOption("numericObserverType", 'n',
    "numeric observer type, 0: gaussian", 0, 0, 2)

  val splitCriterionOption: ClassOption = new ClassOption("splitCriterion", 'c',
    "Split criterion to use.", classOf[SplitCriterion], "InfoGainSplitCriterion")

  val growthAllowedOption: IntOption = new IntOption("growthAllowed", 'g',
    "Whether allow to grow", 1, 0, 1)

  val binaryOnlyOption: IntOption = new IntOption("binaryOnly", 'b',
    "Whether only allow binary splits", 0, 0, 1)

  val numGraceOption: IntOption = new IntOption("numGrace", 'm',
    "The number of instances a leaf should observe between split attempts.",
    50, 1, Int.MaxValue)

  val tieThresholdOption: FloatOption = new FloatOption("tieThreshold", 't',
    "Threshold below which a split will be forced to break ties.", 0.05, 0, 1)

  val splitConfidenceOption: FloatOption = new FloatOption("splitConfidence", 'd',
    "The allowable error in split decision, values closer to 0 will take longer to decide.",
    0.0000001, 0.0, 1.0)

  val learningNodeOption: IntOption = new IntOption("learningNodeType", 'o',
    "learning node type of leaf", 0, 0, 2)

  val nbThresholdOption: IntOption = new IntOption("nbThreshold", 'a',
    "naive bayes threshold", 0, 0, Int.MaxValue)

  val PrePruneOption: IntOption = new IntOption("PrePrune", 'p',
    "whether allow pre-pruning.", 0, 0, 1)

  var model: HoeffdingTreeModel = null

  var exampleLearnerSpecification: ExampleSpecification = null

  override def init(exampleSpecification: ExampleSpecification): Unit = {
    exampleLearnerSpecification = exampleSpecification
    model = new HoeffdingTreeModel(
      numClassesOption.getValue(), numFeaturesOption.getValue(),
      featureArray, numericObserverTypeOption.getValue, splitCriterionOption.getValue(),
      growthAllowedOption.getValue() == 1, binaryOnlyOption.getValue() == 1, numGraceOption.getValue(),
      tieThresholdOption.getValue, splitConfidenceOption.getValue(),
      learningNodeOption.getValue(), nbThresholdOption.getValue(),
      PrePruneOption.getValue() == 1)
    model.init()
  }

  override def getModel: HoeffdingTreeModel = model

  override def train(input: DStream[Example]): Unit = {
    input.foreachRDD {
      rdd =>
        val tmodel = rdd.aggregate(
          new HoeffdingTreeModel(model))(
            (mod, example) => { mod.update(example) }, (mod1, mod2) => mod1.merge(mod2, false))
        model = model.merge(tmodel, true)
    }
  }

  def predict(input: DStream[Example]): DStream[(Example, Double)] = {
    input.map { x => (x, model.predict(x)) }
  }
}

/**
 *
 * Hoeffding Tree or VFDT.
 *
 *
 *
 * A Hoeffding tree is an incremental, anytime decision tree induction algorithm
 *
 * that is capable of learning from massive data streams, assuming that the
 *
 * distribution generating examples does not change over time. Hoeffding trees
 *
 * exploit the fact that a small sample can often be enough to choose an optimal
 *
 * splitting attribute. This idea is supported mathematically by the Hoeffding
 *
 * bound, which quantiﬁes the number of observations (in our case, examples)
 *
 * needed to estimate some statistics within a prescribed precision (in our
 *
 * case, the goodness of an attribute).</p> <p>A theoretically appealing feature
 *
 * of Hoeffding Trees not shared by other incremental decision tree learners is
 *
 * that it has sound guarantees of performance. Using the Hoeffding bound one
 *
 * can show that its output is asymptotically nearly identical to that of a
 *
 * non-incremental learner using inﬁnitely many examples. See for details:</p>
 *
 *
 *
 * <p>G. Hulten, L. Spencer, and P. Domingos. Mining time-changing data streams.
 *
 * In KDD’01, pages 97–106, San Francisco, CA, 2001. ACM Press.</p>
 *
 *
 *
 * <p>Parameters:</p> <ul> <li> -m : Maximum memory consumed by the tree</li>
 *
 * <li> -n : Numeric estimator to use : <ul> <li>Gaussian approximation
 *
 * evaluating 10 splitpoints</li> <li>Gaussian approximation evaluating 100
 *
 * splitpoints</li> <li>Greenwald-Khanna quantile summary with 10 tuples</li>
 *
 * <li>Greenwald-Khanna quantile summary with 100 tuples</li>
 *
 * <li>Greenwald-Khanna quantile summary with 1000 tuples</li> <li>VFML method
 *
 * with 10 bins</li> <li>VFML method with 100 bins</li> <li>VFML method with
 *
 * 1000 bins</li> <li>Exhaustive binary tree</li> </ul> </li> <li> -e : How many
 *
 * instances between memory consumption checks</li> <li> -g : The number of
 *
 * instances a leaf should observe between split attempts</li> <li> -s : Split
 *
 * criterion to use. Example : InfoGainSplitCriterion</li> <li> -c : The
 *
 * allowable error in split decision, values closer to 0 will take longer to
 *
 * decide</li> <li> -t : Threshold below which a split will be forced to break
 *
 * ties</li> <li> -b : Only allow binary splits</li> <li> -z : Stop growing as
 *
 * soon as memory limit is hit</li> <li> -r : Disable poor attributes</li> <li>
 *
 * -p : Disable pre-pruning</li>
 *
 *  <li> -l : Leaf prediction to use: MajorityClass (MC), Naive Bayes (NB) or NaiveBayes
 *
 * adaptive (NBAdaptive).</li>
 *
 *  <li> -q : The number of instances a leaf should observe before
 *
 * permitting Naive Bayes</li>
 *
 * </ul>
 *
 */

class HoeffdingTreeModel(val numClasses: Int, val numFeatures: Int,
                         val featureTypeArray: FeatureTypeArray, val numericObserverType: Int = 0,
                         val splitCriterion: SplitCriterion = new InfoGainSplitCriterion(),
                         var growthAllowed: Boolean = true, val binaryOnly: Boolean = true,
                         val graceNum: Int = 200, val tieThreshold: Double = 0.05,
                         val splitConfedence: Double = 0.0000001, val learningNodeType: Int = 0,
                         val nbThreshold: Int = 0, val prePrune: Boolean = false)
  extends Model with Serializable with Logging {

  type T = HoeffdingTreeModel

  var activeNodeCount: Int = 0
  var inactiveNodeCount: Int = 0
  var deactiveNodeCount: Int = 0
  var decisionNodeCount: Int = 0

  var baseNumExamples: Int = 0
  var blockNumExamples: Int = 0

  var lastExample: Example = null

  var root: Node = null

  var printRootNull: Boolean = true

  def this(model: HoeffdingTreeModel) {
    this(model.numClasses, model.numFeatures, model.featureTypeArray,
      model.numericObserverType, model.splitCriterion, model.growthAllowed,
      model.binaryOnly, model.graceNum, model.tieThreshold, model.splitConfedence,
      model.learningNodeType, model.nbThreshold, model.prePrune)
    activeNodeCount = model.activeNodeCount
    this.inactiveNodeCount = model.inactiveNodeCount
    this.deactiveNodeCount = model.deactiveNodeCount
    this.decisionNodeCount = model.decisionNodeCount
    baseNumExamples = model.baseNumExamples + model.blockNumExamples
    this.root = model.root

    this.lastExample = model.lastExample
  }

  def init(): Unit = {
    root = createLearningNode(learningNodeType, numClasses)
    activeNodeCount += 1
  }

  override def update(point: Example): HoeffdingTreeModel = {
    blockNumExamples += 1
    lastExample = point
    if (root == null) {
      init
    }
    val foundNode = root.filterToLeaf(point, null, -1)
    var leafNode = foundNode.node
    if (leafNode == null) {
      leafNode = createLearningNode(learningNodeType, numClasses)
      foundNode.parent.setChild(foundNode.index, leafNode)
      activeNodeCount += 1
      assert(false)
    }
    if (leafNode.isInstanceOf[LearningNode]) {
      val learnNode = leafNode.asInstanceOf[LearningNode]
      learnNode.learn(this, point)
    }
    this
  }
  /*
   * try to split the learning node
   * @param learnNode the node which may be splitted
   * @param parent parent of the learnNode
   * @param pIndex learnNode's index of the parent
   * @return Unit 
   */
  def attemptToSplit(learnNode: LearningNode, parent: SplitNode, pIndex: Int): Unit = {
    if (growthAllowed && learnNode.isInstanceOf[ActiveLearningNode]) {
      val activeNode = learnNode.asInstanceOf[ActiveLearningNode]
      if (activeNode.addOnWeight() >= graceNum) {
        val isPure = activeNode.isPure()
        if (!isPure) {
          var bestSuggestions: Array[FeatureSplit] = activeNode.getBestSplitSuggestions(splitCriterion, this)
          bestSuggestions = bestSuggestions.sorted
          if (shouldSplit(activeNode, bestSuggestions)) {
            val best: FeatureSplit = bestSuggestions.last
            if (best.conditionalTest == null) {
              deactiveLearningNode(activeNode, parent, pIndex)
            } else {
              val splitNode: SplitNode = new SplitNode(activeNode.classDistribution, best.conditionalTest)
              for (index <- 0 until best.numSplit) {
                splitNode.setChild(index,
                  createLearningNode(learningNodeType, best.distributionFromSplit(index)))
              }
              addSplitNode(splitNode, parent, pIndex)
            }
          }
          // todo manage memory
        }
      }
    }
  }
  /*
   * check whether split the activeNode or not according to Heoffding bound and merit
   * @param activeNode the node which may be splitted
   * @param bestSuggestions array of FeatureSplit
   * @return Boolean
   */
  def shouldSplit(activeNode: ActiveLearningNode, bestSuggestions: Array[FeatureSplit]): Boolean = {
    if (bestSuggestions.length < 2)
      bestSuggestions.length > 0
    else {
      val hoeffdingBound = computeHeoffdingBound(activeNode)
      val length = bestSuggestions.length
      if (hoeffdingBound < tieThreshold ||
        bestSuggestions.last.merit - bestSuggestions(length - 2).merit > hoeffdingBound) {
        true
      } else
        false
    }
  }
  /*
   * merge with another model's FeatureObservers and root, and try to split
   */
  def merge(that: HoeffdingTreeModel, trySplit: Boolean): HoeffdingTreeModel = {
    //    for (i <- 0 until featureObservers.length)
    //      featureObservers(i) = featureObservers(i).merge(that.featureObservers(i), trySplit)

    this.blockNumExamples += that.blockNumExamples
    this.lastExample = that.lastExample
    // merge node
    root.merge(that.root, trySplit)
    if (trySplit) {
      val foundNode = root.filterToLeaf(lastExample, null, -1)
      var leafNode = foundNode.node
      if (leafNode.isInstanceOf[LearningNode]) {
        val learnNode = leafNode.asInstanceOf[LearningNode]
        attemptToSplit(learnNode, foundNode.parent, foundNode.index)
        logInfo(description())
      }
    }
    this
  }
  /* predict the class of point */
  def predict(point: Example): Double = {
    if (root != null) {
      val foundNode = root.filterToLeaf(point, null, -1)
      var leafNode = foundNode.node
      if (leafNode == null) {
        leafNode = foundNode.parent
      }
      argmax(leafNode.classVotes(this, point))
    } else {
      if (printRootNull) {
        printRootNull = false
      }
      0.0
    }
  }

  def createLearningNode(nodeType: Int, classDistribution: Array[Double]): LearningNode = nodeType match {
    case 0 => new ActiveLearningNode(classDistribution, featureTypeArray)
    case 1 => new LearningNodeNB(classDistribution, featureTypeArray)
    case 2 => new LearningNodeNBAdaptive(classDistribution, featureTypeArray)
    case _ => new ActiveLearningNode(classDistribution, featureTypeArray)
  }

  def createLearningNode(nodeType: Int, numClasses: Int): LearningNode = nodeType match {
    case 0 => new ActiveLearningNode(new Array[Double](numClasses), featureTypeArray)
    case 1 => new LearningNodeNB(new Array[Double](numClasses), featureTypeArray)
    case 2 => new LearningNodeNBAdaptive(new Array[Double](numClasses), featureTypeArray)
    case _ => new ActiveLearningNode(new Array[Double](numClasses), featureTypeArray)
  }

  def createLearningNode(nodeType: Int, that: LearningNode): LearningNode = nodeType match {
    case 0 => new ActiveLearningNode(that.asInstanceOf[ActiveLearningNode])
    case 1 => new LearningNodeNB(that.asInstanceOf[LearningNodeNB])
    case 2 => new LearningNodeNBAdaptive(that.asInstanceOf[LearningNodeNBAdaptive])
  }

  def activeLearningNode(inactiveNode: InactiveLearningNode, parent: SplitNode, pIndex: Int): Unit = {
    val activeNode = createLearningNode(learningNodeType, inactiveNode.classDistribution)
    if (parent == null) {
      root = activeNode
    } else {
      parent.setChild(pIndex, activeNode)
    }
    activeNodeCount += 1
    inactiveNodeCount -= 1
  }

  def deactiveLearningNode(activeNode: ActiveLearningNode, parent: SplitNode, pIndex: Int): Unit = {
    val deactiveNode = new InactiveLearningNode(activeNode.classDistribution)
    if (parent == null) {
      root = deactiveNode
    } else {
      parent.setChild(pIndex, deactiveNode)
    }
    activeNodeCount -= 1
    inactiveNodeCount += 1

  }

  def addSplitNode(splitNode: SplitNode, parent: SplitNode, pIndex: Int): Unit = {
    if (parent == null) {
      root = splitNode
    } else {
      parent.setChild(pIndex, splitNode)
    }
    activeNodeCount += splitNode.numChildren() - 1
    decisionNodeCount -= 1
  }

  def computeHeoffdingBound(activeNode: ActiveLearningNode): Double = {
    val rangeMerit = splitCriterion.rangeMerit(activeNode.classDistribution)
    val heoffdingBound = sqrt(rangeMerit * rangeMerit * math_log(1.0 / this.splitConfedence)
      / (activeNode.weight() * 2))
    heoffdingBound
  }

  def description(): String = {
    "Hoeffding Tree Model description:\n" + root.description()
  }
}