// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.processors

import com.intellij.psi.*
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.ElementClassHint.DeclarationKind
import com.intellij.psi.scope.JavaScopeProcessorEvent
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.ProcessorWithHints
import com.intellij.psi.scope.PsiScopeProcessor.Event
import com.intellij.util.SmartList
import com.intellij.util.containers.isNullOrEmpty
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.util.elementInfo
import org.jetbrains.plugins.groovy.lang.resolve.BaseMethodResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.MethodResolveResult
import org.jetbrains.plugins.groovy.lang.resolve.api.Arguments
import org.jetbrains.plugins.groovy.lang.resolve.getName
import org.jetbrains.plugins.groovy.lang.resolve.impl.*
import org.jetbrains.plugins.groovy.lang.resolve.processors.inference.putAll
import org.jetbrains.plugins.groovy.lang.resolve.sorryCannotKnowElementKind

class MethodProcessor(
  private val name: String,
  private val place: PsiElement,
  private val arguments: Arguments?,
  private val typeArguments: Array<out PsiType>
) : ProcessorWithHints(),
    NameHint,
    GroovyResolveKind.Hint,
    ElementClassHint,
    DynamicMembersHint {

  init {
    hint(NameHint.KEY, this)
    hint(GroovyResolveKind.HINT_KEY, this)
    hint(ElementClassHint.KEY, this)
    hint(DynamicMembersHint.KEY, this)
  }

  override fun getName(state: ResolveState): String? = name

  override fun shouldProcess(kind: GroovyResolveKind): Boolean = kind == GroovyResolveKind.METHOD && acceptMore

  override fun shouldProcess(kind: DeclarationKind): Boolean = kind == DeclarationKind.METHOD && acceptMore

  override fun shouldProcessMethods(): Boolean = myCandidates.isEmpty()

  private val myCandidates = SmartList<GroovyMethodResult>()
  private var myApplicable: ApplicabilitiesResult? = null
  private val acceptMore: Boolean get() = myApplicable?.first.isNullOrEmpty()

  override fun execute(element: PsiElement, state: ResolveState): Boolean {
    require(acceptMore) {
      "Don't pass more methods if processor doesn't want to accept them"
    }
    if (element !is PsiMethod) {
      if (state[sorryCannotKnowElementKind] == true) {
        return true
      }
      else {
        error("Unexpected element. ${elementInfo(element)}")
      }
    }
    if (name != getName(state, element)) return true

    if (typeArguments.isNotEmpty()) {
      val newSub = state[PsiSubstitutor.KEY].putAll(element.typeParameters, typeArguments)
      myCandidates += BaseMethodResolveResult(element, place, state.put(PsiSubstitutor.KEY, newSub), arguments)
    }
    else {
      myCandidates += MethodResolveResult(element, place, state, arguments)
    }
    myApplicable = null
    return true
  }

  override fun handleEvent(event: Event, associated: Any?) {
    if (JavaScopeProcessorEvent.CHANGE_LEVEL === event && myApplicable == null) {
      myApplicable = computeApplicableCandidates()
    }
  }

  private fun computeApplicableCandidates(): Pair<List<GroovyMethodResult>, Boolean> {
    return myCandidates
      .correctStaticScope()
      .findApplicable()
  }

  val applicableCandidates: List<GroovyMethodResult>?
    get() {
      val (applicableCandidates, canChooseOverload) = myApplicable ?: computeApplicableCandidates()
      if (applicableCandidates.isEmpty()) return null
      if (canChooseOverload) {
        return chooseOverloads(applicableCandidates, DefaultMethodComparatorContext(place, arguments))
      }
      else {
        return filterBySignature(applicableCandidates)
      }
    }

  val allCandidates: List<GroovyMethodResult> get() = myCandidates
}
