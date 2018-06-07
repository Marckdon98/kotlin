/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.idea.codeInsight

import com.intellij.codeInsight.CodeInsightActionHandler
import com.intellij.codeInsight.navigation.actions.GotoImplementationAction

class KotlinGotoImplementationAction : GotoImplementationAction() {
    override fun getHandler(): CodeInsightActionHandler = KotlinGotoImplementationHandler()
}