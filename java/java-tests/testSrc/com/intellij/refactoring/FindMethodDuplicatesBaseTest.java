/*
 * User: anna
 * Date: 22-Jul-2008
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.util.duplicates.MethodDuplicatesHandler;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.util.ui.UIUtil;

public abstract class FindMethodDuplicatesBaseTest extends LightCodeInsightTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  protected void doTest() throws Exception {
    doTest(true);
  }

  protected void doTest(final boolean shouldSucceed) throws Exception {
    final String filePath = getTestFilePath();
    configureByFile(filePath);
    final PsiElement targetElement = TargetElementUtilBase.findTargetElement(getEditor(), TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on method name", targetElement instanceof PsiMethod);
    final PsiMethod psiMethod = (PsiMethod)targetElement;

    try {
      MethodDuplicatesHandler.invokeOnScope(getProject(), psiMethod, new AnalysisScope(getFile()));
    }
    catch (RuntimeException e) {
      if (shouldSucceed) {
        fail("duplicates were not found");
      }
      return;
    }
    UIUtil.dispatchAllInvocationEvents();
    if (shouldSucceed) {
      checkResultByFile(filePath + ".after");
    }
    else {
      fail("duplicates found");
    }
  }

  protected abstract String getTestFilePath();
}