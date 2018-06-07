/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.TargetElementUtil
import com.intellij.codeInsight.navigation.*
import com.intellij.codeInsight.navigation.ImplementationSearcher.getFlags
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiReference
import com.intellij.usageView.UsageViewShortNameLocation
import java.util.Comparator

class KotlinGotoImplementationHandler : GotoImplementationHandler() {
    override fun getSourceAndTargetElements(editor: Editor, file: PsiFile): GotoTargetHandler.GotoData? {
        val offset = editor.caretModel.offset
        val source = TargetElementUtil.getInstance().findTargetElement(editor, ImplementationSearcher.getFlags(), offset) ?: return null
        return createDataForSource(editor, offset, source)
    }

    private fun ImplementationSearcher.searchImplementations(editor: Editor, element: PsiElement?, offset: Int): Array<PsiElement>? {
        val targetElementUtil = TargetElementUtil.getInstance()
        val onRef = ReadAction.compute<Boolean, RuntimeException> {
            targetElementUtil.findTargetElement(
                editor,
                getFlags() and (TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED or TargetElementUtil.LOOKUP_ITEM_ACCEPTED).inv(),
                offset
            ) == null
        }
        return searchImplementations(element, editor, onRef && ReadAction.compute<Boolean, RuntimeException> {
            element == null || targetElementUtil.includeSelfInGotoImplementation(
                element
            )
        }, onRef)
    }

    private fun createDataForSource(editor: Editor, offset: Int, source: PsiElement): GotoTargetHandler.GotoData? {
        val reference = TargetElementUtil.findReference(editor, offset)
        val instance = TargetElementUtil.getInstance()
        val targets = object : ImplementationSearcher.FirstImplementationsSearcher() {
            override fun accept(element: PsiElement?): Boolean {
                return instance.acceptImplementationForReference(reference, element)
            }

            override fun canShowPopupWithOneItem(element: PsiElement): Boolean {
                return false
            }

        }.searchImplementations(editor, source, offset) ?: return null
        val gotoData = GotoTargetHandler.GotoData(source, targets, emptyList())
        gotoData.listUpdaterTask = object : ImplementationsUpdaterTask(gotoData, editor, offset, reference) {
            override fun onSuccess() {
                super.onSuccess()
                val oneElement = theOnlyOneElement
                if (oneElement != null && navigateToElement(oneElement)) {
                    myPopup.cancel()
                }
            }
        }
        return gotoData
    }

    @Suppress("ObjectLiteralToLambda")
    private open inner class ImplementationsUpdaterTask internal constructor(
        private val gotoData: GotoTargetHandler.GotoData,
        private val editor: Editor,
        private val offset: Int,
        private val reference: PsiReference?
    ) :
        ListBackgroundUpdaterTask(
            gotoData.source.project, ImplementationSearcher.SEARCHING_FOR_IMPLEMENTATIONS, BackgroundUpdaterTask.createComparatorWrapper(
                object : Comparator<PsiElement> {
                    override fun compare(e1: PsiElement, e2: PsiElement): Int {
                        return getRenderer(e1, gotoData).getComparingObject(e1).compareTo(getRenderer(e2, gotoData).getComparingObject(e2))
                    }
                }
            )
        ) {

        override fun run(indicator: ProgressIndicator) {
            super.run(indicator)
            for (element in gotoData.targets) {
                if (!updateComponent(element)) {
                    return
                }
            }
            object : ImplementationSearcher.BackgroundableImplementationSearcher() {
                override fun processElement(element: PsiElement) {
                    indicator.checkCanceled()
                    if (!TargetElementUtil.getInstance().acceptImplementationForReference(reference, element)) return
                    if (gotoData.addTarget(element)) {
                        if (!updateComponent(element)) {
                            indicator.cancel()
                        }
                    }
                }
            }.searchImplementations(editor, gotoData.source, offset)
        }

        override fun getCaption(size: Int): String {
            val name = ElementDescriptionUtil.getElementDescription(gotoData.source, UsageViewShortNameLocation.INSTANCE)
            return getChooserTitle(gotoData.source, name, size, isFinished)
        }
    }
}